package com.mpole.hana.digitaltwin.device.entity;

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

    @Comment("view 테이블로 부터 point_code")
    @Column(name="point_name")
    private String pointName;

    @Comment("schema_definition의 tagName과 매핑되는 논리적 태그명 (예: CurTemp, Voltage)")
    @Column(name="tag_name")
    private String tagName;

    @Comment("디바이스 활성여부")
    private Boolean active;

    @Comment("단위 (예: ℃, kWh, %)")
    private String unit;

    @Column(name="point_type")
    private String pointType;

    //private Boolean alarm;

//    @JdbcTypeCode(SqlTypes.JSON)
//    @Comment("개별 관제점의 데이타 수집/제어 통신 명세")
//    @Column(name = "mapping_info", columnDefinition = "jsonb")
//    private PointMappingInfo pointMappingInfo;

}
