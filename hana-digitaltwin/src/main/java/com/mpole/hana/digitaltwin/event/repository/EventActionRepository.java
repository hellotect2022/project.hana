package com.mpole.hana.digitaltwin.event.repository;

import com.mpole.hana.common.constant.NotificationChannel;
import com.mpole.hana.digitaltwin.event.entity.EventAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventActionRepository extends JpaRepository<EventAction, UUID> {

    List<EventAction> findByEventIdOrderByIssuedAtAsc(UUID eventId);

    /** 클라이언트가 채널 단위로 완료 보고할 때 사용(action_id 를 몰라도 됨). */
    Optional<EventAction> findByEventIdAndChannel(UUID eventId, NotificationChannel channel);
}
