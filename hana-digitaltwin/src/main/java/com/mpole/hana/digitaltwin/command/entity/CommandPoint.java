package com.mpole.hana.digitaltwin.command.entity;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

/**
 * 디바이스 제어 포인트.
 * <p>어떤 디바이스의 어떤 포인트(tbl_device_point)를 제어(command)로 사용할지 지정하고,
 * ON/OFF 로 내보낼 값과 표시용 메타를 관리한다. 실제 외부(XN) 제어에 필요한
 * ref_device_code/ref_point_code 는 참조 포인트({@link DevicePoint})에서 확보한다.</p>
 */
@Entity
@Table(
        name = "tbl_device_command_point",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_command_point_device_point",
                        columnNames = {"device_id", "point_id"}
                )
        },
        indexes = {
                @Index(name = "idx_command_point_device", columnList = "device_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommandPoint extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "command_point_id")
    private Long commandPointId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_command_point_device")
    )
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "point_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_command_point_point")
    )
    @Comment("제어 대상 포인트 (ref_device_code/ref_point_code 확보)")
    private DevicePoint point;

    @Comment("제어 버튼 표시명 (예: 급기팬 기동)")
    private String label;

    @Column(name = "on_value")
    @Comment("ON 명령으로 외부에 전송할 값")
    @Builder.Default
    private String onValue = "1";

    @Column(name = "off_value")
    @Comment("OFF 명령으로 외부에 전송할 값")
    @Builder.Default
    private String offValue = "0";

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Builder.Default
    private Boolean active = true;
}
