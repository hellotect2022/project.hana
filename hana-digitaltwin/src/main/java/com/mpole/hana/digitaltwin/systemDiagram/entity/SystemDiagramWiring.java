package com.mpole.hana.digitaltwin.systemDiagram.entity;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * 계통도 배선(wiring).
 * 두 장비(fromDevice, toDevice)를 잇고, 실제 경로는 배관 세그먼트(segments, JSONB)로 표현한다.
 * segments 의 각 assetId 는 asset_type=PIPE 에셋에 대한 소프트 참조다(DB FK 불가).
 */
@Entity
@Table(name = "tbl_system_diagram_wiring",
        indexes = {
                @Index(name = "idx_system_diagram_wiring_diagram", columnList = "diagram_id"),
                @Index(name = "idx_system_diagram_wiring_from", columnList = "from_device_id"),
                @Index(name = "idx_system_diagram_wiring_to", columnList = "to_device_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemDiagramWiring extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wiring_id")
    private Long wiringId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_system_diagram_wiring_diagram"))
    private SystemDiagram diagram;

    @Column(name = "wiring_name", nullable = false)
    private String wiringName;

    @Column(name = "wiring_type", length = 50)
    private String wiringType;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_device_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_system_diagram_wiring_from"))
    private Device fromDevice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_device_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_system_diagram_wiring_to"))
    private Device toDevice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "segments", columnDefinition = "jsonb DEFAULT '[]'")
    @Builder.Default
    private List<WiringSegment> segments = new ArrayList<>();
}
