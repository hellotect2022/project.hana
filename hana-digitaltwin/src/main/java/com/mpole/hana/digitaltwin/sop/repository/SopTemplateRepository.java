package com.mpole.hana.digitaltwin.sop.repository;

import com.mpole.hana.digitaltwin.sop.entity.SopTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SopTemplateRepository extends JpaRepository<SopTemplate, Long> {
    boolean existsByTemplateCode(String templateCode);
    Optional<SopTemplate> findByTemplateCode(String templateCode);
    List<SopTemplate> findByActiveTrueOrderByTemplateNameAsc();
    List<SopTemplate> findAllByOrderByTemplateNameAsc();
}
