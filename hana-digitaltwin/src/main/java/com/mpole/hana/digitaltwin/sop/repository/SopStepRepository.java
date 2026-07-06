package com.mpole.hana.digitaltwin.sop.repository;

import com.mpole.hana.digitaltwin.sop.entity.SopStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SopStepRepository extends JpaRepository<SopStep, Long> {

    void deleteByTemplate_TemplateId(Long templateId);

    @Query("""
            SELECT DISTINCT s FROM SopStep s
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.buttons
            WHERE s.template.templateId = :templateId
            ORDER BY s.stepNo ASC
            """)
    List<SopStep> findByTemplateIdWithItems(@Param("templateId") Long templateId);
}
