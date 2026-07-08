package com.mpole.hana.digitaltwin.systemDiagram.repository;

import com.mpole.hana.digitaltwin.systemDiagram.entity.SystemDiagram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemDiagramRepository extends JpaRepository<SystemDiagram, Long> {

    List<SystemDiagram> findAllByOrderBySortOrderAscDiagramIdDesc();

    List<SystemDiagram> findByActiveTrueOrderBySortOrderAscDiagramIdDesc();
}
