package com.mpole.hana.digitaltwin.system.service;

import com.mpole.hana.digitaltwin.audit.dto.AuditLogPurgeResult;
import com.mpole.hana.digitaltwin.audit.repository.AccessLogRepository;
import com.mpole.hana.digitaltwin.audit.repository.UsageLogRepository;
import com.mpole.hana.digitaltwin.audit.service.AuditLogPurgeService;
import com.mpole.hana.digitaltwin.system.dto.LogRetentionBatchUpdateRequest;
import com.mpole.hana.digitaltwin.system.dto.LogRetentionPolicyRequest;
import com.mpole.hana.digitaltwin.system.dto.LogRetentionPolicyResponse;
import com.mpole.hana.digitaltwin.system.dto.LogRetentionPurgeResponse;
import com.mpole.hana.digitaltwin.system.entity.LogRetentionPolicy;
import com.mpole.hana.digitaltwin.system.repository.LogRetentionPolicyRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogRetentionService {

    private static final Map<String, String> POLICY_LABELS = Map.of(
            LogRetentionPolicy.KEY_ACCESS_LOG, "접속 로그 (로그인·로그아웃)",
            LogRetentionPolicy.KEY_USAGE_LOG, "사용 로그 (메뉴 진입)"
    );

    private final LogRetentionPolicyRepository policyRepository;
    private final AuditLogPurgeService auditLogPurgeService;


    public List<LogRetentionPolicyResponse> getPolicies() {
        return policyRepository.findAll().stream()
                .sorted((a, b) -> a.getPolicyKey().compareTo(b.getPolicyKey()))
                .map(policy -> LogRetentionPolicyResponse.from(
                        policy,
                        POLICY_LABELS.getOrDefault(policy.getPolicyKey(), policy.getPolicyKey())
                ))
                .toList();
    }

    @Transactional
    public void updatePolicies(LogRetentionBatchUpdateRequest request) {
        for (LogRetentionPolicyRequest item : request.policies()) {
            LogRetentionPolicy policy = policyRepository.findByPolicyKey(item.policyKey())
                    .orElseThrow(() -> new IllegalArgumentException("알 수 없는 정책: " + item.policyKey()));

            policy.updateRetentionDays(item.retentionDays());
        }
    }
    /**
     * 설정된 보관 일수보다 오래된 로그 삭제. 스케줄러·수동 실행 공통.
     */
    @Transactional
    public LogRetentionPurgeResponse purgeExpiredLogs() {
        OffsetDateTime now = OffsetDateTime.now();

        int accessDays = policyRepository.findByPolicyKey(LogRetentionPolicy.KEY_ACCESS_LOG)
                .map(LogRetentionPolicy::getRetentionDays)
                .orElse(90);

        int usageDays = policyRepository.findByPolicyKey(LogRetentionPolicy.KEY_USAGE_LOG)
                .map(LogRetentionPolicy::getRetentionDays)
                .orElse(90);

        OffsetDateTime accessCutoff = now.minusDays(accessDays);
        OffsetDateTime usageCutoff = now.minusDays(usageDays);

        AuditLogPurgeResult auditLogPurgeResult = auditLogPurgeService.purgeBefore(accessCutoff,usageCutoff);

        return new LogRetentionPurgeResponse(auditLogPurgeResult.deletedAccessRows(), auditLogPurgeResult.deletedUsageRows(), accessDays, usageDays);
    }


}
