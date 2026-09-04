package com.mpole.hana.digitaltwin.systemDiagram.repository;

import com.mpole.hana.digitaltwin.systemDiagram.entity.SystemDiagramWiring;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SystemDiagramWiringRepository extends JpaRepository<SystemDiagramWiring, Long> {

    /**
     * 주어진 디바이스 id 집합에 from 또는 to 로 연결된 모든 배선을 배치 조회한다.
     * from/to/diagram 을 join fetch 하여 매핑 시 LAZY N+1 을 방지한다.
     */
    @Query("""
            select w from SystemDiagramWiring w
            join fetch w.fromDevice fd
            join fetch w.toDevice td
            join fetch w.diagram d
            where fd.deviceId in :ids or td.deviceId in :ids
            """)
    List<SystemDiagramWiring> findConnectedToDevices(@Param("ids") Collection<Long> ids);
}
