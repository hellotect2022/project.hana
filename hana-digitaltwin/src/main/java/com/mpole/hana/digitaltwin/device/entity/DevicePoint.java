package com.mpole.hana.digitaltwin.device.entity;

import com.mpole.hana.common.constant.PointDataSource;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tbl_device_point",
        uniqueConstraints = {
                // uk_point_ref 제거: 하나의 외부신호(ref_device_code, ref_point_code)를 여러 device_point 로
                // 팬아웃(1:N)하기 위해 조합 유니크 해제. point_key 유니크는 유지.
                //@UniqueConstraint(name = "uk_point_key", columnNames = {"point_key"}),
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevicePoint extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_id")
    private Long pointId;

    @Column(name = "point_key")
    private String pointKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name="ref_device_code")
    @Comment("SI view 테이블과 연계되는 device_code")
    private String refDeviceCode;

    @Column(name="ref_point_code")
    @Comment("SI view 테이블과 연계되는 point_code")
    private String refPointCode;

    @Comment("포인트 설명 (구 point_name)")
    @Column(name="description")
    private String description;

    @Comment("포인트 표시명")
    @Column(name="point_display_name")
    private String pointDisplayName;

    @Comment("schema_definition의 tagName과 매핑되는 논리적 태그명 (예: CurTemp, Voltage)")
    @Column(name="tag_name")
    private String tagName;

    @Comment("디바이스 활성여부")
    private Boolean active;

    @Comment("단위 (예: ℃, kWh, %)")
    private String unit;

    @Column(name="point_type")
    private String pointType;

    /**
     * 이 포인트 값의 원천 수집 경로.
     *
     * <p>gateway 뷰 스냅샷 파이프라인이 이 값을 보고 자기가 소유하지 않은 포인트·장비를 걸러낸다.
     * 명시하지 않으면(null) 하위호환으로 {@code VIEW01} 로 취급하므로, 컬럼 도입 후 백필 없이도
     * 기존 동작이 유지된다.</p>
     *
     * <p>RabbitMQ 로 들어오는 부스 재실·물리버튼은 {@code AMQP}, 누적 카운트 같은 계산값은
     * {@code DERIVED} 로 지정해야 gateway 기동 시 값이 null 로 덮이지 않는다.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", length = 32)
    @Comment("포인트 값의 원천 수집 경로(VIEW01/VIEW02/MODBUS/TCP/AMQP/MANUAL/DERIVED/COMMAND). null 은 VIEW01 취급")
    private PointDataSource dataSource;

    //private Boolean alarm;

//    @JdbcTypeCode(SqlTypes.JSON)
//    @Comment("개별 관제점의 데이타 수집/제어 통신 명세")
//    @Column(name = "mapping_info", columnDefinition = "jsonb")
//    private PointMappingInfo pointMappingInfo;

}
