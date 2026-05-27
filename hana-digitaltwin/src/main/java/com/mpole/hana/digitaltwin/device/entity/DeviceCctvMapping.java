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
}
