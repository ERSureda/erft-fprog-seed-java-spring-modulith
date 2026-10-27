package com.template.api.shared.infrastructure.adapter.out.persistence.partition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
@Component
@ConditionalOnBean(JdbcTemplate.class)
public class PartitionMaintenanceScheduler {

    private static final long ADVISORY_LOCK_ID = 748291048201L;
    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";
    private static final String MAINTAIN_PARTITIONS_SQL = "CALL shared.sp_maintain_monthly_partitions(?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final int lookaheadMonths;
    private final int lookbehindMonths;

    public PartitionMaintenanceScheduler(
            JdbcTemplate jdbcTemplate,
            @Value("${partition.lookahead-months:3}") int lookaheadMonths,
            @Value("${partition.lookbehind-months:1}") int lookbehindMonths
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.lookaheadMonths = lookaheadMonths;
        this.lookbehindMonths = lookbehindMonths;
    }

    @Scheduled(cron = "${partition.maintenance-cron:0 0 2 1 * ?}")
    public void maintainPartitions() {
        log.info("Attempting to acquire partition maintenance advisory lock...");
        try {
            jdbcTemplate.execute((ConnectionCallback<Void>) this::executeWithSingleConnection);
        } catch (Exception ex) {
            log.error("Failed to execute partition maintenance session: {}", ex.getMessage(), ex);
        }
    }

    private Void executeWithSingleConnection(Connection connection) {
        boolean lockAcquired;
        try {
            lockAcquired = tryAcquireLock(connection);
        } catch (Exception ex) {
            log.error("Failed to query advisory lock for partition maintenance: {}", ex.getMessage(), ex);
            return null;
        }

        if (!lockAcquired) {
            log.info("Partition maintenance skipped: lock is already held by another application instance.");
            return null;
        }

        try {
            log.info("Starting automated monthly partition maintenance (lookahead: {}, lookbehind: {})...",
                    lookaheadMonths, lookbehindMonths);
            try (PreparedStatement stmt = connection.prepareStatement(MAINTAIN_PARTITIONS_SQL)) {
                stmt.setInt(1, lookaheadMonths);
                stmt.setInt(2, lookbehindMonths);
                stmt.execute();
            }
            log.info("Monthly partition maintenance completed successfully.");
        } catch (Exception ex) {
            log.error("Failed to maintain monthly partitions: {}", ex.getMessage(), ex);
        } finally {
            releaseAdvisoryLock(connection);
        }
        return null;
    }

    private boolean tryAcquireLock(Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(TRY_LOCK_SQL)) {
            stmt.setLong(1, ADVISORY_LOCK_ID);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private void releaseAdvisoryLock(Connection connection) {
        try (PreparedStatement stmt = connection.prepareStatement(UNLOCK_SQL)) {
            stmt.setLong(1, ADVISORY_LOCK_ID);
            stmt.execute();
            log.debug("Partition maintenance advisory lock released successfully.");
        } catch (Exception ex) {
            log.warn("Failed to release partition maintenance advisory lock: {}", ex.getMessage(), ex);
        }
    }
}
