package com.mpole.hana.digitaltwin.device.entity;

import jakarta.persistence.*;
import lombok.*;
import jakarta.persistence.Id;

@Entity
@Table(name = "tbl_device_cctv_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceCctvMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_cctv_id")
    private Long deviceCctvId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cctv_id")
    private Device cctv;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    /** 이벤트 CCTV 그리드 배치 순서(관제사 지정). 미지정 시 0 → 매핑 PK 순 폴백. */
    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    /** 대표 카메라 강조 여부(선택). */
    @Column(name = "is_primary")
    @Builder.Default
    private boolean primary = false;
}
