package com.mpole.hana.digitaltwin.system.scheduler;

import com.mpole.hana.digitaltwin.system.service.LogRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogRetentionScheduler {

    private final LogRetentionService logRetentionService;

    @Value("${app.log-retention.purge-enabled:true}")
    private boolean purgeEnabled;

    @Scheduled(cron = "${app.log-retention.purge-cron:0 0 3 * * ?}")
    public void scheduledPurge() {
        if (!purgeEnabled) {
            log.debug("로그 보관 자동 정리 비활성화 (app.log-retention.purge-enabled=false)");
            return;
        }
        try {
            logRetentionService.purgeExpiredLogs();
        } catch (Exception e) {
            log.error("로그 보관 자동 정리 실패", e);
        }
    }
}
