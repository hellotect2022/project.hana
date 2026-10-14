package com.mpole.hana.digitaltwin.sop.dto;

import com.mpole.hana.digitaltwin.sop.entity.SopTemplate;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

/** SOP 템플릿 저작 DTO 묶음 (v2). */
public final class SopTemplateDtos {

    private SopTemplateDtos() {
    }

    /** 템플릿 생성(메타만). body는 빈 문서로 초기화. */
    public record CreateRequest(
            @NotBlank String templateCode,
            @NotBlank String templateName,
            String description,
            String title
    ) {
    }

    /** 템플릿 메타 수정. */
    public record UpdateRequest(
            String templateName,
            String description,
            String title,
            Boolean active
    ) {
    }

    /** 목록/생성 응답(메타). */
    public record SummaryResponse(
            Long templateId,
            String templateCode,
            String templateName,
            String description,
            Boolean active,
            String title,
            Integer revision,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static SummaryResponse from(SopTemplate t) {
            return new SummaryResponse(
                    t.getTemplateId(), t.getTemplateCode(), t.getTemplateName(),
                    t.getDescription(), t.getActive(), t.getTitle(), t.getRevision(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    /** 상세 응답(body 포함). */
    public record DetailResponse(
            Long templateId,
            String templateCode,
            String templateName,
            String description,
            Boolean active,
            String title,
            Integer revision,
            SopTemplateBody body,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static DetailResponse from(SopTemplate t) {
            return new DetailResponse(
                    t.getTemplateId(), t.getTemplateCode(), t.getTemplateName(),
                    t.getDescription(), t.getActive(), t.getTitle(), t.getRevision(),
                    t.getBody(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
