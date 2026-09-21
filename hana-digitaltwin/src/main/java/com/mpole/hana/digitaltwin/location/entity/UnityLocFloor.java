package com.mpole.hana.digitaltwin.location.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_unity_loc_floor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UnityLocFloor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "floor_id")
    private Long floorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private UnityLocBuilding building;

    @Column(name = "floor_name", nullable = false, length = 200)
    private String floorName;

    /**
     * 실제 층수. 지하는 음수(B2F = -2), 옥외처럼 층수가 없는 공간은 의미가 없다.
     * <p>HQS 연동이 층 식별자로 이 값을 받기도 한다
     * ({@code DeviceWcBoothRepository#findBoothOccupancy} 의 {@code florId}).</p>
     */
    @Column(name = "floor_num")
    private Integer floorNum;

    /**
     * 화면 표시 정렬 순서. <b>0 = 최하층</b>, 위로 갈수록 증가한다.
     *
     * <pre>
     *   0  B7F  …  6  B1F   7  옥외   8  1F  …  22  15F   23  PH1F   24  PH2F
     * </pre>
     *
     * <p><b>{@code floorNum} 으로 정렬하면 안 되는 이유</b> — 옥외는 층수가 없어
     * {@code floor_num = 100} 으로 들어가 있다. 그대로 정렬하면 옥외가 PH2F 뒤로 밀린다.
     * 또 {@code floorNum} 은 "실제 층수"라는 별개 의미를 갖고 외부 연동에도 쓰이므로,
     * 정렬 목적으로 값을 바꾸면 그쪽이 깨진다. 그래서 정렬 전용 컬럼을 따로 둔다.</p>
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    @OneToMany(mappedBy = "floor", fetch = FetchType.LAZY)
    @Builder.Default
    private List<UnityLocZone> zones = new ArrayList<>();
}
