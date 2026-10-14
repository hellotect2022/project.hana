package com.mpole.hana.digitaltwin.deviceGroup.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * 장비 논리 그룹 — "무엇끼리인가"만 말하고, "어디에 있나"는 말하지 않는다.
 *
 * <p>zone({@code tbl_unity_loc_zone})과 역할이 다르다. zone 은 3D 콜라이더가 정본이라
 * Unity 가 영역을 그려줘야 존재할 수 있고, 그래서 논리적 묶음을 zone 계층으로 표현하면
 * Unity 작업이 끝날 때까지 장비 배치가 멈춘다. 그 결합을 끊기 위한 테이블이다.</p>
 *
 * <p><b>위치 칼럼이 없는 것은 의도다.</b> 장비 위치는 {@code tbl_device_placement} 가 계속
 * 갖는다. 여기 대표 zone 을 또 두면 멤버 배치와 어긋날 수 있는 두 번째 위치가 생긴다.
 * 어느 화장실/층인지는 {@link #groupCode} 접두사와 {@link #attributes} 로 안다.</p>
 *
 * <p><b>계층(부모-자식)도 두지 않는다.</b> 자기참조 트리를 또 만들면 zone 에서 겪은 문제를
 * 반복한다. 롤스크린 구역의 종류(층 전체/방위/실별)는 {@code attributes.areaKind} 에
 * 문자열로 담는다 — 배선표를 받아 실제 중첩이 필요해지면 그때 칼럼으로 승격한다.</p>
 *
 * <p>장비가 0대인 그룹도 존재한다. 관리자가 그룹을 먼저 만들고 장비를 넣는 흐름을 위해서고,
 * 실제로 {@code WC_1F_M01_B01} 부스가 짝이 될 장비 미등록 상태로 존재한다.</p>
 */
@Entity
@Table(
        name = "tbl_device_group",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_group_code", columnNames = {"group_code"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceGroup extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long groupId;

    /**
     * 외부 연동 식별자. {@code WC_4F_M01_B01} · {@code ROLL_F003_Z001}.
     *
     * <p>HQS {@code wcBoothId} 가 이 값을 그대로 쓴다. 부스는 기존 부스 {@code zone_code} 를
     * 재사용해 계약이 깨지지 않게 했다.</p>
     *
     * <p>PK 가 아니라 별도 칼럼인 이유 — 이 스키마의 표준이 그렇다
     * ({@code device_id}/{@code device_key} · {@code category_id}/{@code category_code} ·
     * {@code zone_id}/{@code zone_code}). 덕분에 명명규칙이 바뀌어도 한 행 UPDATE 로 끝나고
     * 멤버 행을 건드리지 않는다.</p>
     */
    @Column(name = "group_code", nullable = false, length = 100)
    @Comment("외부 연동 식별자 — HQS wcBoothId. 부스는 부스 zone_code 재사용")
    private String groupCode;

    @Column(name = "group_name", nullable = false, length = 200)
    private String groupName;

    /**
     * 그룹의 용도. {@code WC_BOOTH} · {@code ROLLSCREEN} · {@code EV_STATION} · {@code LIGHTING}.
     *
     * <p><b>enum 이 아니라 {@code String} 인 것은 의도다.</b> DB 컬럼은 CHECK 제약 없는
     * {@code varchar(40)} 이라 용도를 나중에 늘릴 수 있는데, {@code @Enumerated} 로 매핑하면
     * 모르는 값을 읽을 때 {@code No enum constant} 로 터져 DB 를 열어둔 의미가 없어진다.
     * 알려진 코드 목록은 {@link DeviceGroupType} 이 화면 select 용으로만 제공한다.</p>
     */
    @Column(name = "group_type", nullable = false, length = 40)
    @Comment("WC_BOOTH · ROLLSCREEN · EV_STATION · LIGHTING (값 추가 가능)")
    private String groupType;

    /** 표시 순서. 부스 B01~B06, 구역 Z000~Z004 처럼 순서가 의미를 갖는 경우에 쓴다. */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    /**
     * 종류별 부가 속성.
     *
     * <p>부스 {@code {"gender":"M","wcCode":"WC_4F_M01"}} ·
     * 롤스크린 {@code {"floor":"F003","areaCode":"Z001","areaKind":"COMPASS","controlRef":"MT.F003.Z001"}}</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    @Comment("종류별 부가값. 롤스크린은 floor·controlRef")
    private Map<String, Object> attributes;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /**
     * 그룹 코드 변경.
     *
     * <p>숫자 PK 라서 이 한 행만 바뀐다 — 멤버는 {@code group_id} 로 묶여 있어 영향이 없다.
     * 중복 검사는 서비스가 먼저 하고, 놓치더라도 {@code uk_device_group_code} 가 막는다.</p>
     *
     * <p>{@link #update} 와 분리한 이유 — 외부 연동 식별자를 바꾸는 일이라
     * 이름·설명 같은 일반 필드 수정과 같은 무게로 다루면 안 된다.</p>
     */
    public void changeGroupCode(String groupCode) {
        if (groupCode != null && !groupCode.isBlank()) {
            this.groupCode = groupCode;
        }
    }

    /**
     * 부분 수정 — null 인 필드는 건드리지 않는다.
     *
     * <p>{@code groupCode} 는 {@link #changeGroupCode} 로 따로 바꾼다.
     * {@code groupType} 은 배타성(한 장비 = 한 용도의 그룹 하나)의 단위라 바꾸면 이미 배정된
     * 멤버의 유효성이 무너진다.</p>
     */
    public void update(String groupName, Integer sortOrder, Map<String, Object> attributes,
                       Boolean active, String description) {
        if (groupName != null) {
            this.groupName = groupName;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        if (attributes != null) {
            this.attributes = attributes;
        }
        if (active != null) {
            this.active = active;
        }
        if (description != null) {
            this.description = description;
        }
    }
}
