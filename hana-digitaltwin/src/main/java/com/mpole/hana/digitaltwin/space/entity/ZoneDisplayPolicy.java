package com.mpole.hana.digitaltwin.space.entity;

import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;
import jakarta.persistence.*;
import lombok.*;

/**
 * 구역(Zone) 표현정책 — 3D에서 어떤 색/투명도/활성으로 표시할지의 정책.
 * 위치(기하)는 UnityLocZone, 상태(ON/OFF/Fault)는 device 집계(buildingLighting)에서 오고,
 * 이 엔티티는 "표시 방법"만 저장한다. (zone × systemType) 당 1개.
 */
@Entity
@Table(
        name = "tbl_zone_display_policy",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_zone_system", columnNames = {"zone_id", "system_type"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ZoneDisplayPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Long policyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private UnityLocZone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_type", nullable = false, length = 32)
    private ZoneSystemType systemType;

    /** 구역명 (예: 조명구역 B1F-01) */
    @Column(name = "display_name", length = 200)
    private String displayName;

    /** 색상 코드 (예: #00CC88) */
    @Column(name = "color_code", length = 16)
    private String colorCode;

    /** 투명도 0~100(%) */
    @Column(name = "opacity")
    @Builder.Default
    private Integer opacity = 30;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** (화재감지 전용) 구역 이벤트 시 피난대피로 자동 표시 연동. 조명은 null */
    @Column(name = "evacuation_auto_show")
    private Boolean evacuationAutoShow;

    /** 표현정책 수정 (위치/시스템 구분은 불변, 표시 속성만 갱신) */
    public void updatePolicy(String displayName, String colorCode, Integer opacity,
                             Boolean active, Boolean evacuationAutoShow) {
        if (displayName != null) this.displayName = displayName;
        if (colorCode != null) this.colorCode = colorCode;
        if (opacity != null) this.opacity = opacity;
        if (active != null) this.active = active;
        this.evacuationAutoShow = evacuationAutoShow;
    }
}
