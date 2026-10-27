package com.template.api.shared.infrastructure.adapter.out.persistence.partition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartitionMaintenanceScheduler Unit Tests")
class PartitionMaintenanceSchedulerTest {

    private static final long ADVISORY_LOCK_ID = 748291048201L;
    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";
    private static final String MAINTAIN_PARTITIONS_SQL = "CALL shared.sp_maintain_monthly_partitions(?, ?)";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement lockStmt;

    @Mock
    private PreparedStatement maintainStmt;

    @Mock
    private PreparedStatement unlockStmt;

    @Mock
    private ResultSet lockRs;

    @Mock
    private ResultSet unlockRs;

    private PartitionMaintenanceScheduler scheduler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        scheduler = new PartitionMaintenanceScheduler(jdbcTemplate, 3, 1);
        lenient().when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<Void> callback = inv.getArgument(0);
            return callback.doInConnection(connection);
        });
    }

    @Test
    @DisplayName("Should acquire lock, execute procedure, and release lock on the same connection")
    void should_acquire_lock_and_execute_procedure_when_maintenance_triggered() throws Exception {
        when(connection.prepareStatement(TRY_LOCK_SQL)).thenReturn(lockStmt);
        when(lockStmt.executeQuery()).thenReturn(lockRs);
        when(lockRs.next()).thenReturn(true);
        when(lockRs.getBoolean(1)).thenReturn(true);

        when(connection.prepareStatement(MAINTAIN_PARTITIONS_SQL)).thenReturn(maintainStmt);

        when(connection.prepareStatement(UNLOCK_SQL)).thenReturn(unlockStmt);

        assertThatCode(() -> scheduler.maintainPartitions()).doesNotThrowAnyException();

        verify(lockStmt).setLong(1, ADVISORY_LOCK_ID);
        verify(maintainStmt).setInt(1, 3);
        verify(maintainStmt).setInt(2, 1);
        verify(maintainStmt).execute();
        verify(unlockStmt).setLong(1, ADVISORY_LOCK_ID);
        verify(unlockStmt).execute();
    }

    @Test
    @DisplayName("Should skip maintenance when advisory lock is already held by another instance")
    void should_skip_maintenance_when_lock_already_held_by_another_instance() throws Exception {
        when(connection.prepareStatement(TRY_LOCK_SQL)).thenReturn(lockStmt);
        when(lockStmt.executeQuery()).thenReturn(lockRs);
        when(lockRs.next()).thenReturn(true);
        when(lockRs.getBoolean(1)).thenReturn(false);

        assertThatCode(() -> scheduler.maintainPartitions()).doesNotThrowAnyException();

        verify(connection, never()).prepareStatement(MAINTAIN_PARTITIONS_SQL);
        verify(connection, never()).prepareStatement(UNLOCK_SQL);
    }

    @Test
    @DisplayName("Should catch and handle exception gracefully and still release lock when procedure fails")
    void should_handle_exception_gracefully_and_release_lock_when_procedure_fails() throws Exception {
        when(connection.prepareStatement(TRY_LOCK_SQL)).thenReturn(lockStmt);
        when(lockStmt.executeQuery()).thenReturn(lockRs);
        when(lockRs.next()).thenReturn(true);
        when(lockRs.getBoolean(1)).thenReturn(true);

        when(connection.prepareStatement(MAINTAIN_PARTITIONS_SQL)).thenReturn(maintainStmt);
        when(maintainStmt.execute()).thenThrow(new SQLException("Lock acquisition conflict in catalog"));

        when(connection.prepareStatement(UNLOCK_SQL)).thenReturn(unlockStmt);

        assertThatCode(() -> scheduler.maintainPartitions()).doesNotThrowAnyException();

        verify(maintainStmt).execute();
        verify(unlockStmt).setLong(1, ADVISORY_LOCK_ID);
        verify(unlockStmt).execute();
    }

    @Test
    @DisplayName("Should handle exception gracefully when lock query itself fails")
    void should_handle_exception_gracefully_when_lock_query_fails() throws Exception {
        when(connection.prepareStatement(TRY_LOCK_SQL)).thenThrow(new SQLException("Connection pool timeout"));

        assertThatCode(() -> scheduler.maintainPartitions()).doesNotThrowAnyException();

        verify(connection, never()).prepareStatement(MAINTAIN_PARTITIONS_SQL);
        verify(connection, never()).prepareStatement(UNLOCK_SQL);
    }
}
