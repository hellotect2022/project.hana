package com.mpole.hana.digitaltwin.sop.repository;

import com.mpole.hana.digitaltwin.sop.entity.SopTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SopTransitionRepository extends JpaRepository<SopTransition, Long> {

    void deleteByTemplate_TemplateId(Long templateId);

    List<SopTransition> findByTemplate_TemplateId(Long templateId);
}
