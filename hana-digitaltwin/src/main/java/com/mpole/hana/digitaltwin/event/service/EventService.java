package com.mpole.hana.digitaltwin.event.service;

import com.mpole.hana.digitaltwin.device.service.DeviceCategoryCascadeService;
import com.mpole.hana.digitaltwin.event.dto.AlarmHistoryRow;
import com.mpole.hana.digitaltwin.event.dto.AlarmHistoryStats;
import com.mpole.hana.digitaltwin.event.dto.AlarmRuleOption;
import com.mpole.hana.digitaltwin.event.dto.EventResponse;
import com.mpole.hana.digitaltwin.event.entity.Event;
import com.mpole.hana.digitaltwin.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 이벤트 조회 서비스.
 *
 * <p>발생·해소·재활성·확인(ack) 쓰기는 전부 event-server(R2DBC, 웹소켓 ACK)가 담당한다.
 * digitaltwin 은 조회만 하므로 클래스 전체가 readOnly 다.
 * (2026-10-06 삭제: 원시 로그 getHistory · REST ack · 화면 조립 getDisplay/buildSop/buildCctv)</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final DeviceCategoryCascadeService deviceCategoryCascadeService;

    /**
     * 알람 이력 화면 조회 — 명칭(장비명)·위치(층)·알람명·완료여부·발생일시.
     *
     * <p>전력감시·엘리베이터·소방방재·태양광·출입통제·화장실 물리버튼 화면이 모두 이 하나를 쓴다.
     * 화면마다 다른 건 제목과 고정 필터뿐이다.</p>
     *
     * @param categoryId 소분류가 아니어도 된다 — 대·중분류를 넘기면 하위 소분류 전체로 확장한다
     * @param completed  true=완료(확인됨), false=미완료(미확인), null=전체 (판정 기준은 {@code ackedAt}, 2026-10-02 확정)
     * @param q          검색어 — 알람명·장비명·장비키 부분일치(대소문자 무시). 2026-10-01 추가
     */
    public Page<AlarmHistoryRow> getAlarmHistory(
            String level,
            Long ruleId,
            Long deviceId,
            Long floorId,
            Long categoryId,
            Boolean completed,
            String q,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    ) {
        List<Long> categoryIds = (categoryId == null)
                ? null
                : deviceCategoryCascadeService.resolveSmallCategoryIdsForFilter(categoryId);

        // 하위 소분류가 하나도 없으면 결과도 없다. 빈 목록을 IN 절에 넘기면 SQL 문법 오류가 난다.
        if (categoryIds != null && categoryIds.isEmpty()) {
            return Page.empty(pageable);
        }

        return eventRepository.searchAlarmHistory(
                level, ruleId, deviceId, floorId, categoryIds, completed, likeOf(q), from, to, pageable);
    }

    /** 이력 화면 상단 집계 — {@link #getAlarmHistory} 와 같은 필터로 전체/완료/미완료 건수. */
    public AlarmHistoryStats getAlarmHistoryStats(
            String level,
            Long ruleId,
            Long deviceId,
            Long floorId,
            Long categoryId,
            String q,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        List<Long> categoryIds = (categoryId == null)
                ? null
                : deviceCategoryCascadeService.resolveSmallCategoryIdsForFilter(categoryId);
        if (categoryIds != null && categoryIds.isEmpty()) {
            return AlarmHistoryStats.of(0, 0);
        }
        Object[] row = eventRepository.countAlarmHistory(
                level, ruleId, deviceId, floorId, categoryIds, likeOf(q), from, to);
        // 집계 쿼리는 항상 1행이지만 JPA 가 Object[] 안에 Object[] 를 한 겹 더 싸서 줄 때가 있다.
        Object[] cells = (row.length == 1 && row[0] instanceof Object[] inner) ? inner : row;
        long total = cells[0] == null ? 0 : ((Number) cells[0]).longValue();
        long completed = cells[1] == null ? 0 : ((Number) cells[1]).longValue();
        return AlarmHistoryStats.of(total, completed);
    }

    /** 검색어 → JPQL LIKE 패턴(소문자, 양쪽 %). 비어 있으면 null → 조건에서 제외. */
    private static String likeOf(String q) {
        if (q == null || q.isBlank()) return null;
        return "%" + q.trim().toLowerCase() + "%";
    }

    /**
     * 알람명 드롭다운("데이터 선택") 선택지 — 실제로 발생한 적 있는 규칙만.
     *
     * @param from 이 시각 이후 발생분만. null 이면 전체 기간
     */
    public List<AlarmRuleOption> getAlarmRuleOptions(OffsetDateTime from) {
        List<AlarmRuleOption> options = new ArrayList<>();
        for (Object[] row : eventRepository.findDistinctRules(from)) {
            Long id = row[0] == null ? null : ((Number) row[0]).longValue();
            options.add(new AlarmRuleOption(id, (String) row[1]));
        }
        return options;
    }

    public EventResponse get(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다. id=" + id));
        return EventResponse.from(event);
    }

    /** 현재 활성(미해소, clearedAt 없음) 이벤트 목록 — "지금 떠 있는 알람". */
    public List<EventResponse> getActive() {
        return eventRepository.findByClearedAtIsNullOrderByOccurredAtDesc().stream()
                .map(EventResponse::from)
                .toList();
    }
}
