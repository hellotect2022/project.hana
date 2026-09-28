package com.mpole.hana.digitaltwin.deviceGroup.entity;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

/**
 * 그룹 ↔ 장비. "어느 그룹에 어느 장비가, 무슨 일로, 몇 번째로".
 *
 * <p>그룹 하나에 장비 여러 대가 들어간다 — 부스는 조명·재실·버튼 3대, 롤스크린 구역은
 * 제어기 1대 + 모터 다수. 이 N:1 을 {@code tbl_device} 에 칼럼을 붙이는 대신 별도
 * 테이블로 둔 것은 기존 장비 로직을 건드리지 않기 위해서다.</p>
 *
 * <p><b>같은 장비가 같은 용도의 그룹 여러 곳에 속할 수 있다.</b> 롤스크린 모터 한 대가
 * "3층 전체"와 "3층 서쪽"에 동시에 속하는 것은 정상이다. 남아 있는 제약은
 * {@code uk_dgm_group_device (group_id, device_id)} 하나 — 같은 그룹에 같은 장비를
 * 두 번 넣는 것만 막는다.</p>
 *
 * <p>대가도 적어 둔다. 재실센서 하나가 실수로 두 부스에 배정되면 {@code boothStatus} 가
 * 두 부스에 같은 재실 상태를 보여주고, 에러도 행 수 이상도 없어 <b>버그인지 실제인지
 * 구분할 수 없다.</b> 관리자 화면은 후보 제외로 막지만 직접 SQL·일괄 업로드는 무방비다.</p>
 *
 * <p><b>FK 를 Hibernate 가 만들지 않도록 {@code NO_CONSTRAINT} 로 막아 두었다.</b>
 * 이 프로젝트는 {@code ddl-auto: update} 로 뜨는데, DB 에 이미 이름 붙은 FK
 * ({@code fk_dgm_group}, {@code tbl_device_group_member_device_id_fkey})가 있어서
 * Hibernate 가 자기 이름으로 하나 더 붙이는 것을 막는다. 제약은 DB 소관이다
 * (마이그레이션 {@code 2026-09-22-device-group-v3-role.sql} 이 정본).</p>
 */
@Entity
@Table(
        name = "tbl_device_group_member",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dgm_group_device", columnNames = {"group_id", "device_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceGroupMember extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "group_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private DeviceGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Device device;

    /**
     * 이 그룹 안에서 하는 일 — {@code CONTROLLER}(제어 명령을 받음) · {@code MONITOR}(상태를 보고함).
     *
     * <p>쿼리가 갈라야 하는 것이 그 둘이다. 장비 종류(조명·센서·버튼)는 {@code category} 가
     * 이미 말해주므로 여기서 다시 적지 않는다.</p>
     *
     * <p><b>enum 이 아니라 {@code String} 인 것은 의도다</b> — {@link DeviceGroupRole} 주석 참고.
     * 알려진 코드 목록은 화면 select 용으로만 쓰고, 여기 없는 값도 저장·조회된다.</p>
     */
    @Column(name = "role", length = 40)
    @Comment("CONTROLLER(제어대상) · MONITOR(상태조회). 값 추가 가능")
    private String role;

    /** 그룹 안에서의 표시 순서. 저장 시 목록 인덱스를 그대로 부여하므로 비거나 겹치지 않는다. */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
