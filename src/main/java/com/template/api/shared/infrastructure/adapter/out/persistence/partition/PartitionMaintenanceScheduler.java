package com.template.api.shared.infrastructure.adapter.out.persistence.partition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(JdbcTemplate.class)
public class PartitionMaintenanceScheduler {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "${partition.maintenance-cron:0 0 2 1 * ?}")
    public void maintainPartitions() {
        log.info("Starting automated monthly partition maintenance...");
        try {
            jdbcTemplate.execute("CALL shared.sp_maintain_monthly_partitions(3, 1)");
            log.info("Monthly partition maintenance completed successfully.");
        } catch (Exception ex) {
            log.error("Failed to maintain monthly partitions: {}", ex.getMessage(), ex);
        }
    }
}
