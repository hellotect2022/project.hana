package com.mpole.hana.digitaltwin.systemDiagram.entity;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 계통도 소속 서브 장비 매핑.
 * 하나의 계통도(diagram)에 동일 장비(device)는 한 번만 매핑된다.
 */
@Entity
@Table(name = "tbl_system_diagram_device",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_system_diagram_device", columnNames = {"diagram_id", "device_id"})
        },
        indexes = {
                @Index(name = "idx_system_diagram_device_device", columnList = "device_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemDiagramDevice extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Long mappingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_system_diagram_device_diagram"))
    private SystemDiagram diagram;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_system_diagram_device_device"))
    private Device device;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
