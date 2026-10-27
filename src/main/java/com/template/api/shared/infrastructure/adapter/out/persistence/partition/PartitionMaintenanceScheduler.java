package com.template.api.shared.infrastructure.adapter.out.persistence.partition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

        Boolean lockAcquired;
        try {
            lockAcquired = jdbcTemplate.queryForObject(TRY_LOCK_SQL, Boolean.class, ADVISORY_LOCK_ID);
        } catch (Exception ex) {
            log.error("Failed to query advisory lock for partition maintenance: {}", ex.getMessage(), ex);
            return;
        }

        if (!Boolean.TRUE.equals(lockAcquired)) {
            log.info("Partition maintenance skipped: lock is already held by another application instance.");
            return;
        }

        try {
            log.info("Starting automated monthly partition maintenance (lookahead: {}, lookbehind: {})...",
                    lookaheadMonths, lookbehindMonths);
            jdbcTemplate.update(MAINTAIN_PARTITIONS_SQL, lookaheadMonths, lookbehindMonths);
            log.info("Monthly partition maintenance completed successfully.");
        } catch (Exception ex) {
            log.error("Failed to maintain monthly partitions: {}", ex.getMessage(), ex);
        } finally {
            releaseAdvisoryLock();
        }
    }

    private void releaseAdvisoryLock() {
        try {
            jdbcTemplate.queryForObject(UNLOCK_SQL, Boolean.class, ADVISORY_LOCK_ID);
            log.debug("Partition maintenance advisory lock released successfully.");
        } catch (Exception ex) {
            log.warn("Failed to release partition maintenance advisory lock: {}", ex.getMessage(), ex);
        }
    }
}
