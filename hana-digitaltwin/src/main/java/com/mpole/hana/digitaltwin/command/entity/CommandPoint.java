package com.mpole.hana.digitaltwin.command.entity;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * 디바이스 제어 포인트 — <b>어떤 포인트를 어떤 방식으로 조작할지</b>의 설정.
 *
 * <p>어떤 디바이스의 어떤 포인트({@code tbl_device_point})를 제어로 사용할지 지정하고,
 * 외부로 내보낼 값과 표시용 메타를 관리한다. 실제 외부(XN) 제어에 필요한
 * {@code ref_device_code}/{@code ref_point_code} 는 참조 포인트({@link DevicePoint})에서 확보한다.</p>
 *
 * <h3>유형별로 쓰는 필드가 다르다</h3>
 * <pre>
 *   TOGGLE  onValue / offValue (+ onLabel / offLabel)     예) ud   올림·내림
 *   ENUM    options                                        예) stop 정지(옵션 1개)
 *   RANGE   minValue / maxValue / stepValue / unit         예) pos  0~100 %
 * </pre>
 * 유형별 필수값 검증은 {@code CommandPointService.validate()},
 * 전송값 확정은 {@code DeviceControlService.resolveSendValue()} 가 담당한다.
 *
 * <h3>관계</h3>
 * <pre>
 *   tbl_device       ◀── device_id (FK)  ┐
 *                                        ├─ CommandPoint ─▶ 제어 실행 시 point 의 ref 코드로 XN 중계
 *   tbl_device_point ◀── point_id  (FK)  ┘
 *                     UNIQUE(device_id, point_id) → 장비의 포인트 하나당 제어 1건
 * </pre>
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

    /** 이 값에 따라 화면 컨트롤과 전송값 검증이 갈린다. 기존 데이터 호환을 위해 기본값은 TOGGLE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 16)
    @Builder.Default
    @Comment("제어 유형. TOGGLE=두 값 전환(on_value/off_value) / ENUM=옵션 중 선택(options) / RANGE=숫자 입력(min~max). 유형에 따라 화면 컨트롤과 전송값 검증이 달라진다")
    private CommandType commandType = CommandType.TOGGLE;

    /**
     * TOGGLE 전용. 다른 유형에서는 <b>null</b> 이다 —
     * 유형과 무관한 값은 저장하지 않는다({@code CommandPointService.applyTypeFields}).
     */
    @Column(name = "on_value")
    @Comment("[TOGGLE] ON 명령으로 외부에 전송할 값. 다른 유형에서는 null")
    private String onValue;

    @Column(name = "off_value")
    @Comment("[TOGGLE] OFF 명령으로 외부에 전송할 값. 다른 유형에서는 null")
    private String offValue;

    @Column(name = "on_label", length = 32)
    @Comment("[TOGGLE] ON 버튼 표시명(예: 올림). 비우면 ON 으로 표시")
    private String onLabel;

    @Column(name = "off_label", length = 32)
    @Comment("[TOGGLE] OFF 버튼 표시명(예: 내림). 비우면 OFF 로 표시")
    private String offLabel;

    @Column(name = "min_value")
    @Comment("[RANGE] 허용 최솟값(포함). 범위 밖 값은 서버가 거부한다")
    private Double minValue;

    @Column(name = "max_value")
    @Comment("[RANGE] 허용 최댓값(포함)")
    private Double maxValue;

    /** 0.5 를 주면 22.5 같은 소수 설정값도 받는다. 비우면 단위 제약 없이 min~max 안이면 통과. */
    @Column(name = "step_value")
    @Comment("[RANGE] 입력 단위. 1=정수만, 0.5=반값 허용. 비우면 단위 제약 없음")
    private Double stepValue;

    @Column(name = "unit", length = 16)
    @Comment("[RANGE] 화면 표시용 단위(%, ℃ 등). 전송값에는 포함되지 않는다")
    private String unit;

    /**
     * ENUM 선택지. {@code [{"value":"1","label":"정지"}]} 형태.
     * 옵션이 1개면 버튼 하나짜리 순간 명령이 된다(별도 MOMENTARY 유형을 두지 않는 이유).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "jsonb")
    @Comment("[ENUM] 선택 가능한 값 목록 [{\"value\":\"1\",\"label\":\"정지\"}]. 옵션 1개면 버튼 1개(순간 명령)로 렌더된다")
    private List<CommandOption> options;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Builder.Default
    private Boolean active = true;

    /** ENUM 선택지 1건. */
    public record CommandOption(String value, String label) {
    }
}
