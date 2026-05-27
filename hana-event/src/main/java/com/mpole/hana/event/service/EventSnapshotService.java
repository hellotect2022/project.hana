package com.mpole.hana.event.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.event.config.ReactiveEventQueue;
import com.mpole.hana.event.dto.EventHistoryDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventSnapshotService {

    private final DatabaseClient postgresDatabaseClient;
    private final ObjectMapper objectMapper;
    private final ReactiveEventQueue reactiveEventQueue;

    @PostConstruct
    public void startEventSnapshotPipeline() {
        reactiveEventQueue.getEventHistoryFlux()
                .publishOn(Schedulers.newParallel("event-snapshot-thread"))
                .flatMap(event->{
                    log.info("event snapshot 용으로 수신하는 flux 데이타 {}",event);
                    return insertEventHistory(event);
                })
                .subscribe()
        ;
    }

    public Mono<Void> insertEventHistory(EventHistoryDto event) {
        // [핵심] 1. 같은 배치 내에서 동일 device_id는 '가장 최신 것' 하나만 남깁니다.
        // 이렇게 하면 "cannot affect row a second time" 에러가 원천 차단됩니다.

        try {
            String jsonStr = objectMapper.writeValueAsString(event.data());
            // 2. 전체 Bulk Upsert 쿼리 생성
            String sql = """
            INSERT INTO tbl_device_event_history (event_id, device_id, event_type, is_processed, data, created_at)
            VALUES (:eventId, :deviceId, :eventType, :isProcessed, :data::jsonb, :createdAt);
            """;

            var time = System.currentTimeMillis();
            return postgresDatabaseClient.sql(sql)
                    .bind("eventId",event.eventId())
                    .bind("deviceId",event.data().deviceId())
                    .bind("eventType",event.eventType())
                    .bind("isProcessed",event.isProcessed())
                    .bind("data",jsonStr)
                    .bind("createdAt",event.data().timestamp())
                    .then()
                    .doOnSuccess(a->log.info("event insert 하는데 걸린시간 ㅎㅎ ㅜㅜ {}ms",System.currentTimeMillis()- time))
                    .doOnError(e -> log.error("event history insert 실행 중 DB 에러: {}", e.getMessage()));

        } catch (Exception e) {
            return Mono.error(new RuntimeException("JSON 직렬화 실패", e));
        }
    }
}
