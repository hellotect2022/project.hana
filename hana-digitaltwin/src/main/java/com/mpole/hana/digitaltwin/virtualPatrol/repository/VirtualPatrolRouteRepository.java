package com.mpole.hana.digitaltwin.virtualPatrol.repository;

import com.mpole.hana.digitaltwin.virtualPatrol.entity.VirtualPatrolRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VirtualPatrolRouteRepository extends JpaRepository<VirtualPatrolRoute, String> {

    /** 즐겨찾기 슬롯 재지정 시 기존 점유 경로 확인용 */
    Optional<VirtualPatrolRoute> findByBookmarkSlot(Integer bookmarkSlot);
}
