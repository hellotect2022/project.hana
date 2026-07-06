package com.mpole.hana.digitaltwin.sop.dto;

import com.mpole.hana.digitaltwin.sop.entity.SopTemplate;

public record SopTemplateSummaryResponse(
        Long templateId,
        String templateCode,
        String templateName,
        String description,
        Boolean active,
        String title,
        Integer timeoutMinutes,
        Integer revision
) {
    public static SopTemplateSummaryResponse from(SopTemplate template) {
        return new SopTemplateSummaryResponse(
                template.getTemplateId(),
                template.getTemplateCode(),
                template.getTemplateName(),
                template.getDescription(),
                template.getActive(),
                template.getTitle(),
                template.getTimeoutMinutes(),
                template.getRevision()
        );
    }
}
