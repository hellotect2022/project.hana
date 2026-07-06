package com.mpole.hana.digitaltwin.sop.repository;

import com.mpole.hana.digitaltwin.sop.entity.SopActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SopActionLogRepository extends JpaRepository<SopActionLog, Long> {
}
