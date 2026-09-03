package com.mpole.hana.gateway.ingestion.dbview.view01;

import com.mpole.hana.common.dto.XnPointData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>외부 뷰 #01 변경 감지(delta) 필터.</b>
 *
 * <p><b>왜 필요한가</b> — {@link View01ReadService} 는 누락 방지를 위해 매 폴링 풀리드를 하지만,
 * 그 결과를 그대로 하류(Kafka/event/DB)로 흘리면 초당 10만 건이 무의미하게 재발행된다.
 * 직전 폴링 대비 실제로 바뀐 포인트만 통과시켜 부하를 실변경 수준으로 낮춘다.</p>
 *
 * <p><b>상태/재기동</b> — 직전값을 인메모리 맵으로만 들고 있다(포인트 키당 1개, 약 10만 엔트리 = 수십 MB).
 * 재기동하면 맵이 비어 첫 폴링은 전량 통과하지만, 하류의 현재값 upsert(멱등)·이력 append
 * ({@code ON CONFLICT DO NOTHING})가 흡수하므로 안전하다.</p>
 *
 * <p><b>주의(회귀 영향)</b> — 이 필터로 raw flux 가 delta 가 되면 하류 소비자 전부(현재값/알람/웹소켓/이력)가
 * delta 를 받는다. 알람 sustain(지속시간) 재평가와 웹소켓 전체스냅샷 의존은 별도 보정이 필요할 수 있다.</p>
 */
@Service
@Slf4j
public class View01ChangeFilter {

    /** key = deviceCode:pointCode, value = 직전 시그니처. */
    private final ConcurrentHashMap<String, String> lastSeen = new ConcurrentHashMap<>();

    /**
     * 직전 폴링 대비 변경된 포인트만 남긴다.
     * <p>현재 시그니처는 {@code update_datetime} 이다(아래 주석의 valueRaw|alarmYn 조합은 보류 상태).</p>
     */
    public List<XnPointData> retainChanged(List<XnPointData> all) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<XnPointData> changed = new ArrayList<>();
        for (XnPointData p : all) {
            String key = p.deviceCode() + ":" + p.pointCode();
            // 값이 없던(신규) 포인트이거나 시그니처가 달라졌으면 변경으로 판정 (현재는 시간기준으로 수정)
            //String signature = p.valueRaw() + "|" + p.alarmYn();
            String signature = Optional.ofNullable(p.updateDatetime()).map(OffsetDateTime::toString).orElse("");
            String prev = lastSeen.put(key, signature);
            if (!signature.equals(prev)) {
                changed.add(p);
            }
        }
        return changed;
    }
}
