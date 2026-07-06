package com.mpole.hana.digitaltwin.sop.repository;

import com.mpole.hana.digitaltwin.sop.entity.SopInstanceItemState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SopInstanceItemStateRepository extends JpaRepository<SopInstanceItemState, Long> {
    List<SopInstanceItemState> findByInstanceId(Long instanceId);
    Optional<SopInstanceItemState> findByInstanceIdAndItemId(Long instanceId, Long itemId);
}
