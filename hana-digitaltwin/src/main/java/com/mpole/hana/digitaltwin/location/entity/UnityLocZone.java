package com.mpole.hana.digitaltwin.location.entity;

import jakarta.persistence.*;
import lombok.*;
import jakarta.persistence.Id;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 구역(zone) — 층 안의 공간. <b>자기참조 트리</b>다.
 *
 * <pre>
 *   건물 → 층 → 구역(depth 0) → 자식 구역(depth 1)
 *
 *   예) 4F → "남자화장실"(depth 0) → "AM-01"~"AM-05"(depth 1, 부스)
 * </pre>
 *
 * <p>계층을 둔 이유 — 화장실/부스, 충전소, 주차구역처럼 "공간 안의 공간"이 반복되고,
 * 외부 연동(HQS)의 {@code wcId}/{@code wcBoothId}/{@code stationId} 가 전부 공간 식별자라
 * 구역 하나로 통일하면 매핑이 단순해진다.</p>
 *
 * <p><b>{@code floor} 는 자식도 직접 들고 있다.</b> 부모를 타고 올라가 층을 찾게 만들면
 * 층별 조회가 전부 복잡해지기 때문이다(의도한 비정규화).</p>
 */
@Entity
@Table(name = "tbl_unity_loc_zone")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UnityLocZone {

    /** 허용 최대 depth. 0=구역, 1=자식구역 → 2단까지만. */
    public static final int MAX_DEPTH = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zone_id")
    private Long zoneId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private UnityLocFloor floor;

    @Column(name = "zone_name", nullable = false, length = 200)
    private String zoneName;

    @Column(name = "zone_mesh_name", length = 200)
    private String zoneMeshName;

    /** 메쉬 표현 구분: "2D" | "3D". WA-ZONE 추가는 2D, 조명/화재 구역 추가는 3D */
    @Column(name = "mesh_type", length = 8)
    @Builder.Default
    private String meshType = "2D";

    /** 상위 구역. null 이면 층 직속 최상위 구역이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_parent_id",
            foreignKey = @ForeignKey(name = "fk_zone_parent"))
    @Comment("상위 구역(자기참조). NULL 이면 층 직속 최상위 구역")
    private UnityLocZone parent;

    /**
     * 하위 구역. 조회 편의용이며 <b>cascade 를 걸지 않는다</b> —
     * 부모를 지울 때 자식과 그 장비 배치가 조용히 사라지면 안 되기 때문이다.
     * (DB 도 FK 를 RESTRICT 로 두어 자식이 있으면 삭제를 막는다)
     */
    @OneToMany(mappedBy = "parent")
    @Builder.Default
    private List<UnityLocZone> children = new ArrayList<>();

    /**
     * 외부 연동용 안정 식별자(HQS {@code stationId}/{@code wcId}/{@code wcBoothId}).
     * <p>{@code zone_name} 은 UNIQUE 가 아니라("남자화장실"이 층마다 있다) 키로 쓸 수 없다.
     * 값이 있으면 유일해야 하고(부분 유니크 인덱스), 없으면 null 이다.</p>
     */
    @Column(name = "zone_code", length = 200)
    @Comment("외부 연동용 안정 식별자. 값이 있으면 유일")
    private String zoneCode;

    /**
     * 0=최상위 구역, 1=자식 구역. 부모로부터 파생되며 직접 지정하지 않는다.
     * <p>3D 구역 판정에서 "가장 깊은 구역"을 고르는 기준이 된다 — 중첩된 콜라이더에
     * 레이를 쐈을 때 바깥(화장실)이 아니라 안쪽(부스)을 선택해야 하기 때문이다.</p>
     */
    @Column(name = "depth", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    @Comment("0=최상위 구역, 1=자식 구역")
    private Integer depth = 0;

    /**
     * 구역 종류별 부가 속성. 예: {@code {"type":"TOILET","gender":"M"}}
     * <p>device 의 {@code property_info} 와 같은 자리다 — 종류마다 다른 속성을
     * 컬럼으로 늘리지 않기 위해 JSONB 로 둔다.</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    @Comment("구역 종류별 부가 속성(JSONB)")
    private Map<String, Object> attributes;

    /** 구역(존) 정보 수정 — 층/이름/메쉬명/메쉬타입 갱신 */
    public void updateInfo(UnityLocFloor floor, String zoneName, String zoneMeshName, String meshType) {
        this.floor = floor;
        this.zoneName = zoneName;
        this.zoneMeshName = zoneMeshName;
        if (meshType != null) this.meshType = meshType;
    }

    /**
     * 계층·식별자 정보 수정.
     * <p>{@code depth} 는 인자로 받지 않고 <b>부모로부터 파생</b>한다 —
     * 호출부가 직접 넣게 두면 트리와 어긋난 값이 들어갈 수 있다.</p>
     */
    public void updateHierarchy(UnityLocZone parent, String zoneCode, Map<String, Object> attributes) {
        this.parent = parent;
        this.depth = (parent == null) ? 0 : parent.getDepth() + 1;
        this.zoneCode = zoneCode;
        this.attributes = attributes;
    }

    /** 최상위 구역인가(층 직속). */
    public boolean isRoot() {
        return parent == null;
    }
}
