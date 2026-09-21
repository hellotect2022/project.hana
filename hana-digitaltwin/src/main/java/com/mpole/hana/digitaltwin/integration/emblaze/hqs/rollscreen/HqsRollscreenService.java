package com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen;

import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenGroupItem;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenListData;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenListRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 전동롤스크린 목록 조회 — HQS 연동.
 *
 * <h3>매핑</h3>
 * <pre>
 *   groupId     ← device_id
 *   groupName   ← device_name          (F010_M001 형태)
 *   openPercent ← 100 - pos-st         ← 반전. 아래 설명 참고
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HqsRollscreenService {

    /**
     * 값이 없을 때 내보낼 개방률.
     * <p>명세의 {@code openPercent} 는 정수라 "모름"을 표현할 코드가 없다.
     * 수집 전이거나 통신이 끊긴 스크린은 0(닫힘)으로 나간다.</p>
     */
    private static final int UNKNOWN_OPEN_PERCENT = 0;

    private final RollscreenRepository rollscreenRepository;

    @Transactional(readOnly = true)
    public HqsRollscreenListData getRollscreenList(HqsRollscreenListRequest request) {
        List<Object[]> floorRows = rollscreenRepository.resolveFloor(request.florId());
        if (floorRows.isEmpty()) {
            // 없는 층이면 빈 목록을 준다 — 요청한 florId 를 그대로 돌려줘 무엇을 물었는지 남긴다.
            log.warn("전동롤스크린 조회: 알 수 없는 florId={}", request.florId());
            return HqsRollscreenListData.builder()
                    .floorId(request.florId())
                    .floorName(null)
                    .groupList(List.of())
                    .build();
        }

        Object[] floor = floorRows.get(0);
        Integer floorNum = floor[0] == null ? null : ((Number) floor[0]).intValue();
        String floorName = floor[1] == null ? null : floor[1].toString();

        List<HqsRollscreenGroupItem> groups = new ArrayList<>();
        for (Object[] row : rollscreenRepository.findRollscreensByFloor(floorNum)) {
            Long deviceId = row[0] == null ? null : ((Number) row[0]).longValue();
            if (deviceId == null) {
                continue;
            }
            groups.add(HqsRollscreenGroupItem.builder()
                    .groupId(String.valueOf(deviceId))
                    .groupName(row[1] == null ? null : row[1].toString())
                    .openPercent(toOpenPercent(row[2] == null ? null : row[2].toString()))
                    .build());
        }

        return HqsRollscreenListData.builder()
                .floorId(floorNum == null ? request.florId() : String.valueOf(floorNum))
                .floorName(floorName)
                .groupList(groups)
                .build();
    }

    /**
     * {@code pos-st} → HQS {@code openPercent}. <b>값을 뒤집는다.</b>
     *
     * <pre>
     *   우리 pos-st       최상단 = 0(개방)   최하단 = 100(차광)
     *   HQS openPercent   100 = 완전열림     0 = 닫힘
     * </pre>
     *
     * <p>그대로 흘리면 <b>완전히 열린 스크린이 "닫힘"으로 나간다.</b> 현재 대부분의 값이
     * {@code 0.0}(= 완전 개방)이라 반전을 빠뜨리면 HQS 화면 전체가 닫힘으로 보인다.</p>
     *
     * <p>원본은 {@code "0.0"} 같은 실수 문자열이라 반올림해 정수로 만들고 0~100 으로 자른다.</p>
     */
    private static int toOpenPercent(String posStRaw) {
        if (posStRaw == null || posStRaw.isBlank()) {
            return UNKNOWN_OPEN_PERCENT;
        }
        try {
            long position = Math.round(Double.parseDouble(posStRaw.trim()));
            long open = 100L - position;
            return (int) Math.max(0, Math.min(100, open));
        } catch (NumberFormatException e) {
            log.warn("pos-st 파싱 실패 raw='{}' → {} 로 응답", posStRaw, UNKNOWN_OPEN_PERCENT);
            return UNKNOWN_OPEN_PERCENT;
        }
    }
}
