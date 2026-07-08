package com.mpole.hana.digitaltwin.systemDiagram.entity;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 계통도(system diagram).
 * 마스터 장비(master_device)를 중심으로 소속 서브 장비(devices)와 배선(wirings)을 관리한다.
 * 좌표는 Unity 월드좌표이며 zone/building/floor 개념은 없다.
 */
@Entity
@Table(name = "tbl_system_diagram",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_system_diagram_code", columnNames = {"diagram_code"})
        },
        indexes = {
                @Index(name = "idx_system_diagram_master", columnList = "master_device_id"),
                @Index(name = "idx_system_diagram_sort", columnList = "sort_order")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemDiagram extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diagram_id")
    private Long diagramId;

    @Column(name = "diagram_name", nullable = false)
    private String diagramName;

    @Column(name = "diagram_code", length = 200)
    private String diagramCode;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_device_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_system_diagram_master_device"))
    private Device masterDevice;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "diagram", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SystemDiagramDevice> devices = new ArrayList<>();

    @OneToMany(mappedBy = "diagram", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SystemDiagramWiring> wirings = new ArrayList<>();

    public void addDevice(SystemDiagramDevice device) {
        device.setDiagram(this);
        this.devices.add(device);
    }

    public void addWiring(SystemDiagramWiring wiring) {
        wiring.setDiagram(this);
        this.wirings.add(wiring);
    }

    public void clearDevices() {
        this.devices.clear();
    }

    public void clearWirings() {
        this.wirings.clear();
    }
}
