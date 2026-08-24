package com.mpole.hana.digitaltwin.virtualPatrol.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * 가상순찰 경로 — 이름으로 저장하고, 경로가 갖는 지점 리스트를 JSONB 한 컬럼에 담는다.
 *
 * <p>{@code bookmarkSlot}(즐겨찾기 슬롯)은 별도 테이블 대신 nullable·unique 컬럼으로 관리한다
 * (경로 하나가 슬롯 하나를 점유하는 1:1 선택 관계 — 슬롯당 1경로는 unique 제약 + 서비스 재지정 로직으로 보장).</p>
 */
@Entity
@Table(
        name = "tbl_virtual_patrol_route",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_vpr_bookmark_slot", columnNames = {"bookmark_slot"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VirtualPatrolRoute extends DateEntity {

    /** PK — 클라이언트(Unity)가 생성해 넘기는 UUID 문자열(자동생성 아님). */
    @Id
    @Column(name = "route_id", length = 36)
    private String routeId;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "bookmark_slot")
    @Comment("즐겨찾기 슬롯(0-base). null=미즐겨찾기, 슬롯당 1경로(unique)")
    private Integer bookmarkSlot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "waypoints", columnDefinition = "jsonb")
    @Comment("경로 지점 리스트(JSONB 배열)")
    private List<VirtualPatrolWaypoint> waypoints;

    /** 전체 교체(full update) — Unity 가 경로 객체 전체를 보낸다는 전제. bookmarkSlot 은 null 이면 즐겨찾기 해제. */
    public void update(String displayName, Integer bookmarkSlot, List<VirtualPatrolWaypoint> waypoints) {
        if (displayName != null) {
            this.displayName = displayName;
        }
        this.bookmarkSlot = bookmarkSlot;
        if (waypoints != null) {
            this.waypoints = waypoints;
        }
    }
}
