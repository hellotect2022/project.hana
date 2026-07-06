package com.mpole.hana.digitaltwin.event.repository;

import com.mpole.hana.digitaltwin.event.entity.EventInstance;
import com.mpole.hana.digitaltwin.event.entity.EventInstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventInstanceRepository extends JpaRepository<EventInstance, Long> {

    List<EventInstance> findByStatusInOrderByOccurredAtDesc(List<EventInstanceStatus> statuses);
}
