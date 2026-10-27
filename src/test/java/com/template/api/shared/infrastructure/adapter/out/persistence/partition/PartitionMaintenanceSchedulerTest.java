package com.template.api.shared.infrastructure.adapter.out.persistence.partition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartitionMaintenanceScheduler Unit Tests")
class PartitionMaintenanceSchedulerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private PartitionMaintenanceScheduler scheduler;

    @Test
    @DisplayName("Should execute procedure call successfully")
    void should_execute_procedure_call_successfully_when_scheduler_runs() {
        assertDoesNotThrow(() -> scheduler.maintainPartitions());

        verify(jdbcTemplate).execute(eq("CALL shared.sp_maintain_monthly_partitions(3, 1)"));
    }

    @Test
    @DisplayName("Should catch and handle exception gracefully when execution fails")
    void should_handle_exception_gracefully_when_execution_fails() {
        doThrow(new DataAccessException("Connection failed") {})
                .when(jdbcTemplate)
                .execute(eq("CALL shared.sp_maintain_monthly_partitions(3, 1)"));

        assertDoesNotThrow(() -> scheduler.maintainPartitions());

        verify(jdbcTemplate).execute(eq("CALL shared.sp_maintain_monthly_partitions(3, 1)"));
    }
}
