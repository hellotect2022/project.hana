package com.mpole.hana.digitaltwin.sop.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * SOP 템플릿 (v2 — JSONB 문서 방식).
 * 저작 구조 전체를 body(components[])에 담는다.
 */
@Entity
@Table(name = "tbl_sop_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SopTemplate extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "template_code", nullable = false, unique = true, length = 100)
    @Comment("템플릿 코드 (예: FIRE_DETECT)")
    private String templateCode;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column
    @Comment("SOP 패널 제목")
    private String title;

    @Column(nullable = false)
    @Builder.Default
    @Comment("body 저장 시 증가 (진행 중 인스턴스 스냅샷 고정용)")
    private Integer revision = 1;

    // v2 컴포넌트 문서. DB nullable(=기존 테이블에 ddl-auto ALTER 안전) — 비어있지 않음 보장은 저장 검증(Step 3)에서.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body", columnDefinition = "jsonb")
    @Comment("v2 컴포넌트 문서 (components[])")
    private SopTemplateBody body;
}
