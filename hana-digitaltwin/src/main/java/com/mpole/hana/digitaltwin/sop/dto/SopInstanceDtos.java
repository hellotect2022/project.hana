package com.mpole.hana.digitaltwin.sop.dto;

import com.mpole.hana.digitaltwin.sop.entity.SopInstance;
import com.mpole.hana.digitaltwin.sop.enums.SopInstanceStatus;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;

/** SOP 런타임(인스턴스) DTO 묶음. */
public final class SopInstanceDtos {

    private SopInstanceDtos() {
    }

    /** 인스턴스 생성. eventId 는 선택(event 재연결 전 단독 생성 허용). */
    public record CreateRequest(
            @NotNull Long templateId,
            Long eventId
    ) {
    }

    /** 결과 저장(방식 B) — 실행 상태를 한 번에. result 는 자유 JSONB. */
    public record SaveResultRequest(
            Map<String, Object> result,
            SopInstanceStatus status,
            String resultStatus,
            Integer currentStepNo
    ) {
    }

    /** 인스턴스 조회 응답 = 스냅샷 + 결과 + 메타. */
    public record InstanceResponse(
            Long instanceId,
            Long templateId,
            String templateCode,
            Long eventId,
            SopInstanceStatus status,
            Integer currentStepNo,
            Integer lockedRevision,
            String resultStatus,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            SopTemplateBody structureSnapshot,
            Map<String, Object> result,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static InstanceResponse from(SopInstance i) {
            return new InstanceResponse(
                    i.getInstanceId(), i.getTemplateId(), i.getTemplateCode(), i.getEventId(),
                    i.getStatus(), i.getCurrentStepNo(), i.getLockedRevision(), i.getResultStatus(),
                    i.getStartedAt(), i.getCompletedAt(),
                    i.getStructureSnapshot(), i.getResult(),
                    i.getCreatedAt(), i.getUpdatedAt());
        }
    }
}
