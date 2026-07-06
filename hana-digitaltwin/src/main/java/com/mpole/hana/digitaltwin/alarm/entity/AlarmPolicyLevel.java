package com.mpole.hana.digitaltwin.alarm.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 알람 정책의 심각도 등급 상세.
 * THRESHOLD 조건에서는 thresholdValue(AI 임계치)로 등급을 구분하며,
 * icon/effect/displayOrder 로 표현을 관리한다.
 */
@Entity
@Table(name = "tbl_alarm_policy_level")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlarmPolicyLevel extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "level_id")
    private Long levelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private AlarmPolicy policy;

    @Enumerated(EnumType.STRING)
    private AlarmLevel level;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    private String icon;

    private String effect;

    @Column(name = "display_order")
    @Builder.Default
    private int displayOrder = 0;
}
