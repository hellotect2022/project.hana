package com.mpole.hana.event.pipeline.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.event.config.sink.ReactiveQueue;
import com.mpole.hana.event.dto.EventHistoryDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * <b>장비 값 번들 이력</b> 적재 — 이름과 달리 이벤트 도메인이 아니다.
 *
 * <p>웹소켓 싱크로 흐르는 {@link EventHistoryDto} 중 {@code data}(DeviceBundle)가 있는 것만
 * {@code tbl_device_event_history} 에 INSERT 한다. 이벤트 정본({@code tbl_event})은
 * {@link EventWriter} 가 따로 쓴다.</p>
 *
 * <p><b>현재 동작하지 않는다</b> — {@link #startEventSnapshotPipeline()} 의 {@code .subscribe()} 가
 * 주석 처리돼 있어 스트림이 구독되지 않는다(기존 상태 유지). 살릴지 폐기할지는 별도 판단 사항.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventHistoryService {

    private final DatabaseClient postgresDatabaseClient;
    private final ObjectMapper objectMapper;
    private final ReactiveQueue reactiveQueue;

    @PostConstruct
    public void startEventSnapshotPipeline() {
        reactiveQueue.getEventHistoryFlux()
                .publishOn(Schedulers.newParallel("event-snapshot-thread"))
                .flatMap(event->{
                    log.info("event snapshot 용으로 수신하는 flux 데이타 {}",event);
                    return insertEventHistory(event);
                })
                //.subscribe()
        ;
    }

    public Mono<Void> insertEventHistory(EventHistoryDto event) {
        // EVENT/EVENT_CLEAR 봉투는 data(DeviceBundle) 가 없다(정본은 tbl_event 에 EventWriter 가
        // 별도 저장). 여기서 data() 역참조 시 NPE 이므로 skip — 이 서비스는 장비 값 스냅샷 전용.
        if (event.data() == null) {
            return Mono.empty();
        }

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
                    // 봉투의 isProcessed 는 2026-10-01 삭제(항상 false 였음). 컬럼은 남아 있어 상수로 채운다.
                    .bind("isProcessed", false)
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
