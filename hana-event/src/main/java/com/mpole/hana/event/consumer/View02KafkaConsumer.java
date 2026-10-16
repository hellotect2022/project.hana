package com.mpole.hana.event.consumer;

import com.mpole.hana.common.dto.InfraStatus;
import com.mpole.hana.event.pipeline.netstatus.NetStatusService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.KafkaReceiver;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>뷰 #02(중계기 네트워크 상태) Kafka 수신.</b>
 *
 * <p>뷰 #01 과 달리 <b>Sink 를 거치지 않고 곧바로 처리 계층({@link NetStatusService})으로 넘긴다</b> —
 * 소비자가 하나뿐이라 복제(fan-out)가 필요 없기 때문이다.
 * 뷰 #01 은 현재값·이력·알람 등 여러 소비자가 같은 스트림을 봐야 해서 Sink 로 복제한다.</p>
 *
 * <p>한 배치에 같은 인프라가 여러 번 오면 <b>마지막(최신) 값만</b> 남긴다 — 스냅샷이라
 * 중간 값을 굳이 DB 에 쓸 이유가 없다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hdt.dbview.view02", name = "enabled", havingValue = "true")
public class View02KafkaConsumer {

    private final KafkaReceiver<String, InfraStatus> view02KafkaReceiver;
    private final NetStatusService netStatusService;

    @PostConstruct
    public void start() {
        view02KafkaReceiver.receive()
                .bufferTimeout(500, Duration.ofMillis(500))
                .publishOn(Schedulers.newSingle("view02-consume-thread"))
                .concatMap(records -> {
                    // 같은 인프라가 한 배치에 여러 번 오면 마지막(최신) 값만 남긴다.
                    Map<String, InfraStatus> latest = new LinkedHashMap<>();
                    records.forEach(r -> {
                        InfraStatus s = r.value();
                        if (s != null && s.refInfraCode() != null) {
                            latest.put(s.refInfraCode(), s);
                        }
                        r.receiverOffset().acknowledge();
                    });
                    return netStatusService.applySnapshot(List.copyOf(latest.values()))
                            .doOnError(e -> log.error("[View02] 배치 처리 실패(다음 배치에서 수렴)", e))
                            .onErrorResume(e -> Mono.empty());
                })
                .subscribe();
        log.info("[View02] 인프라 상태 수신 파이프라인 시작");
    }
}
