package com.mpole.hana.digitaltwin.alarm.service;

import com.mpole.hana.digitaltwin.alarm.dto.AlarmHistoryPageResponse;
import com.mpole.hana.digitaltwin.alarm.dto.AlarmHistoryResponse;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmHistory;
import com.mpole.hana.digitaltwin.alarm.repository.AlarmHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 알람 발생 이력 조회(읽기 전용) 서비스.
 * 쓰기는 event-server(R2DBC)가 담당하며, 이 서비스는 조회만 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmHistoryService {

    private final AlarmHistoryRepository alarmHistoryRepository;

    /**
     * level / deviceId / occurredAt(from~to) 선택 필터로 알람 이력을 페이징 조회한다.
     * 각 필터는 null 이면 조건에서 제외된다. 정렬(occurredAt desc)은 Pageable 에서 지정한다.
     */
    public AlarmHistoryPageResponse getHistory(
            String level,
            Long deviceId,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    ) {
        Page<AlarmHistoryResponse> page = alarmHistoryRepository
                .findByOptionalFilters(level, deviceId, from, to, pageable)
                .map(AlarmHistoryResponse::from);
        return AlarmHistoryPageResponse.from(page);
    }

    public AlarmHistoryResponse get(Long id) {
        AlarmHistory history = alarmHistoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("알람 이력을 찾을 수 없습니다. id=" + id));
        return AlarmHistoryResponse.from(history);
    }
}
