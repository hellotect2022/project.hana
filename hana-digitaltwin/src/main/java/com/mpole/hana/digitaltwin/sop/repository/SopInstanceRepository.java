package com.mpole.hana.digitaltwin.sop.repository;

import com.mpole.hana.digitaltwin.sop.entity.SopInstance;
import com.mpole.hana.digitaltwin.sop.entity.SopInstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SopInstanceRepository extends JpaRepository<SopInstance, Long> {
    Optional<SopInstance> findByEventId(Long eventId);
    List<SopInstance> findByStatusInOrderByCreatedAtDesc(List<SopInstanceStatus> statuses);
}
