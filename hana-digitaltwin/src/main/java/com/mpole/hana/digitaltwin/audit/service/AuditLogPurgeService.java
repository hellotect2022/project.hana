package com.mpole.hana.digitaltwin.audit.service;

import com.mpole.hana.digitaltwin.audit.dto.AuditLogPurgeResult;
import com.mpole.hana.digitaltwin.audit.repository.AccessLogRepository;
import com.mpole.hana.digitaltwin.audit.repository.UsageLogRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogPurgeService {

    private final AccessLogRepository accessLogRepository;
    private final UsageLogRepository usageLogRepository;

    @Transactional
    public AuditLogPurgeResult purgeBefore(OffsetDateTime accessCutoff, OffsetDateTime usageCutoff) {
        int deletedAccess = accessLogRepository.deleteByCreatedAtBefore(accessCutoff);
        int deletedUsage = usageLogRepository.deleteByCreatedAtBefore(usageCutoff);
        log.info("audit 로그 정리: 접속 {}건, 사용 {}건", deletedAccess, deletedUsage);
        return new AuditLogPurgeResult(deletedAccess, deletedUsage);
    }
}
