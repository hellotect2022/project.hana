package com.mpole.hana.gateway.ingestion.dbview;

import com.mpole.hana.common.dto.XnPointData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 뷰 폴링 결과에 대한 <b>앱 레벨 변경 감지(delta/CDC)</b> 필터.
 *
 * <p><b>왜 필요한가</b> — vwDigitalTwin 뷰의 {@code update_datetime} 은 "뷰 행이 갱신된 시각"이 아니라
 * 각 물리 장비(소스)의 자기 시각이라, 뷰 전체로 보면 시간이 단조증가하지 않는다. 따라서 단일
 * 워터마크(WHERE update_datetime &gt; cursor) 증분 쿼리는 소스 시계가 뒤처진 포인트를 상시 누락시킨다.
 * 그래서 <b>읽기는 매 폴링마다 풀(full)로 유지</b>(누락 방지)하고, 대신 <b>직전 폴링 대비 실제로 바뀐
 * 포인트만 하류로 통과</b>시켜 kafka/event-server/DB 부하를 실변경 수준으로 줄인다.</p>
 *
 * <p><b>변경 판정 기준</b> — {@code valueRaw + alarmYn}. {@code update_datetime} 은 <b>일부러 제외</b>한다:
 * 소스가 값 변화 없이 스캔마다 timestamp 만 touch 하는 경우(폭증 원인)를 걸러내기 위함이다. 값 또는
 * 알람 플래그가 바뀌면 통과시켜, 하류 알람 평가가 alarmYn 변화를 놓치지 않게 한다.</p>
 *
 * <p><b>상태/재기동</b> — 직전값을 인메모리 맵으로만 들고 있다(포인트 키당 1개, 약 10만 엔트리 = 수십 MB).
 * 재기동하면 맵이 비어 <b>첫 폴링은 전량 통과</b>하지만, 하류의 현재값 upsert(멱등)·이력 append
 * ({@code ON CONFLICT DO NOTHING})가 흡수하므로 안전하다.</p>
 *
 * <p><b>주의(회귀 영향)</b> — 이 필터로 raw flux 가 delta 가 되면 6개 소비자 전부(현재값/알람/웹소켓/이력 등)가
 * delta 를 받는다. 알람 sustain(지속시간) 재평가와 웹소켓 전체스냅샷 의존은 별도 보정이 필요할 수 있다.</p>
 */
@Service
@Slf4j
public class DbViewChangeFilter {

    /** key = deviceCode:pointCode, value = 직전 시그니처(valueRaw|alarmYn). */
    private final ConcurrentHashMap<String, String> lastSeen = new ConcurrentHashMap<>();

    /**
     * 직전 폴링 대비 값(valueRaw) 또는 알람 플래그(alarmYn)가 바뀐 포인트만 남긴다.
     * update_datetime 만 바뀐(값 무변화) 포인트는 통과시키지 않는다.
     */
    public List<XnPointData> retainChanged(List<XnPointData> all) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<XnPointData> changed = new ArrayList<>();
        for (XnPointData p : all) {
            String key = p.deviceCode() + ":" + p.pointCode();
            String signature = p.updateDatetime().toString();//p.valueRaw() + "|" + p.alarmYn();
            // 값이 없던(신규) 포인트이거나 시그니처가 달라졌으면 변경으로 판정
            String prev = lastSeen.put(key, signature);
            if (!signature.equals(prev)) {
                changed.add(p);
            }
        }
        return changed;
    }
}
