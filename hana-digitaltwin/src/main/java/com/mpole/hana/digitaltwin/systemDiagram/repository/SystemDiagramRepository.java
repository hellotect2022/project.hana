package com.mpole.hana.digitaltwin.systemDiagram.repository;

import com.mpole.hana.digitaltwin.systemDiagram.entity.SystemDiagram;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SystemDiagramRepository extends JpaRepository<SystemDiagram, Long> {

    List<SystemDiagram> findAllByOrderBySortOrderAscDiagramIdDesc();

    List<SystemDiagram> findByActiveTrueOrderBySortOrderAscDiagramIdDesc();

    /** 계통도명/코드 부분일치(대소문자 무시) 페이지 검색. kw 는 반드시 non-null(빈 검색은 findAll 사용). */
    @Query("SELECT d FROM SystemDiagram d WHERE "
            + "LOWER(d.diagramName) LIKE LOWER(CONCAT('%', :kw, '%')) "
            + "OR LOWER(COALESCE(d.diagramCode, '')) LIKE LOWER(CONCAT('%', :kw, '%'))")
    Page<SystemDiagram> search(@Param("kw") String kw, Pageable pageable);
}
