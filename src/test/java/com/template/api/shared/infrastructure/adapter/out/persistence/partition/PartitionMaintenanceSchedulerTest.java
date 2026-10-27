package com.template.api.shared.infrastructure.adapter.out.persistence.partition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private PartitionMaintenanceScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PartitionMaintenanceScheduler(jdbcTemplate, 3, 1);
    }

    @Test
    @DisplayName("Should acquire lock, execute procedure, and release lock successfully")
    void should_acquire_lock_execute_procedure_and_release_lock_successfully() {
        when(jdbcTemplate.queryForObject(eq(TRY_LOCK_SQL), eq(Boolean.class), eq(ADVISORY_LOCK_ID)))
                .thenReturn(Boolean.TRUE);
        when(jdbcTemplate.queryForObject(eq(UNLOCK_SQL), eq(Boolean.class), eq(ADVISORY_LOCK_ID)))
                .thenReturn(Boolean.TRUE);

        assertDoesNotThrow(() -> scheduler.maintainPartitions());

        verify(jdbcTemplate).queryForObject(eq(TRY_LOCK_SQL), eq(Boolean.class), eq(ADVISORY_LOCK_ID));
        verify(jdbcTemplate).update(eq(MAINTAIN_PARTITIONS_SQL), eq(3), eq(1));
        verify(jdbcTemplate).queryForObject(eq(UNLOCK_SQL), eq(Boolean.class), eq(ADVISORY_LOCK_ID));
    }

    @Test
    @DisplayName("Should skip maintenance when advisory lock is already held by another instance")
    void should_skip_maintenance_when_lock_already_held_by_another_instance() {
        when(jdbcTemplate.queryForObject(eq(TRY_LOCK_SQL), eq(Boolean.class), eq(ADVISORY_LOCK_ID)))
                .thenReturn(Boolean.FALSE);

        assertDoesNotThrow(() -> scheduler.maintainPartitions());

        verify(jdbcTemplate).queryForObject(eq(TRY_LOCK_SQL), eq(Boolean.class), eq(ADVISORY_LOCK_ID));
        verify(jdbcTemplate, never()).update(eq(MAINTAIN_PARTITIONS_SQL), any(), any());
        verify(jdbcTemplate, never()).queryForObject(eq(UNLOCK_SQL), eq(Boolean.class), eq(ADVISORY_LOCK_ID));
    }

    @Test
    @DisplayName("Should catch and handle exception gracefully and still release lock when procedure fails")
    void should_handle_exception_gracefully_and_release_lock_when_procedure_fails() {
        when(jdbcTemplate.queryForObject(eq(TRY_LOCK_SQL), eq(Boolean.class), eq(ADVISORY_LOCK_ID)))
                .thenReturn(Boolean.TRUE);
        when(jdbcTemplate.update(eq(MAINTAIN_PARTITIONS_SQL), eq(3), eq(1)))
                .thenThrow(new DataAccessException("Lock acquisition conflict in catalog") {});

        assertDoesNotThrow(() -> scheduler.maintainPartitions());

        verify(jdbcTemplate).queryForObject(eq(TRY_LOCK_SQL), eq(Boolean.class), eq(ADVISORY_LOCK_ID));
        verify(jdbcTemplate).update(eq(MAINTAIN_PARTITIONS_SQL), eq(3), eq(1));
        verify(jdbcTemplate).queryForObject(eq(UNLOCK_SQL), eq(Boolean.class), eq(ADVISORY_LOCK_ID));
    }

    @Test
    @DisplayName("Should handle exception gracefully when lock query itself fails")
    void should_handle_exception_gracefully_when_lock_query_fails() {
        when(jdbcTemplate.queryForObject(eq(TRY_LOCK_SQL), eq(Boolean.class), eq(ADVISORY_LOCK_ID)))
                .thenThrow(new DataAccessException("Connection pool timeout") {});

        assertDoesNotThrow(() -> scheduler.maintainPartitions());

        verify(jdbcTemplate).queryForObject(eq(TRY_LOCK_SQL), eq(Boolean.class), eq(ADVISORY_LOCK_ID));
        verify(jdbcTemplate, never()).update(eq(MAINTAIN_PARTITIONS_SQL), any(), any());
        verify(jdbcTemplate, never()).queryForObject(eq(UNLOCK_SQL), eq(Boolean.class), any());
    }
}
