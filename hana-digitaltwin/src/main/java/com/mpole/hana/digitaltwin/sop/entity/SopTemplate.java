package com.mpole.hana.digitaltwin.sop.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

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

    @Column(name = "timeout_minutes", nullable = false)
    @Builder.Default
    private Integer timeoutMinutes = 10;

    @Column(nullable = false)
    @Builder.Default
    @Comment("구조 저장 시 증가 (진행 중 인스턴스 고정용)")
    private Integer revision = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unity_layout", columnDefinition = "jsonb")
    private Map<String, Object> unityLayout;
}
