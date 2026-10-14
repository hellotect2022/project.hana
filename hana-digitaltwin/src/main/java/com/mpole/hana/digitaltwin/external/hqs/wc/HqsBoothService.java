package com.mpole.hana.digitaltwin.external.hqs.wc;

import com.mpole.hana.digitaltwin.external.common.BoothOccupancy;
import com.mpole.hana.digitaltwin.external.hqs.config.HqsProperties;
import com.mpole.hana.digitaltwin.external.hqs.wc.dto.HqsBoothStatusData;
import com.mpole.hana.digitaltwin.external.hqs.wc.dto.HqsBoothStatusRequest;
import com.mpole.hana.digitaltwin.external.hqs.wc.dto.HqsWcBoothItem;
import com.mpole.hana.digitaltwin.external.hqs.wc.dto.HqsWcItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 부스 재실 현황 — 장비 그룹 기준.
 *
 * <p>구 {@code HqsApiService.getBoothStatusData}(zone 계층 기준)를 대체한다. 응답 형식은
 * <b>그때와 완전히 같다</b> — 요청·응답 DTO 를 새로 만들지 않고 {@code wc/dto} 의 것을
 * 그대로 쓴다. 바뀐 것은 데이터를 어디서 가져오는가 하나다.</p>
 *
 * <p>전환 전에 두 구현을 나란히 띄우고 8개 필터 조합으로 대조해 72/72 행이 일치하는 것을
 * 확인했다.</p>
 */
@Service
@RequiredArgsConstructor
public class HqsBoothService {

    private final HqsProperties hqsProperties;
    private final HqsBoothRepository hqsBoothRepository;

    /**
     * 화장실 단위로 묶어 응답을 만든다.
     *
     * <p>쿼리가 {@code ORDER BY wcCode, group_code} 로 정렬해 주므로
     * {@link LinkedHashMap} 이 그 순서를 그대로 유지한다.</p>
     */
    @Transactional(readOnly = true)
    public HqsBoothStatusData getBoothStatusData(HqsBoothStatusRequest request) {
        List<Object[]> rows = hqsBoothRepository.findBoothOccupancyByGroup(
                blankToNull(request.florId()),
                blankToNull(request.wcId()),
                blankToNull(request.gender()),
                (int) (hqsProperties.getVacancyTimeoutMs() / 1000L));

        Map<String, HqsWcItem> byWcId = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String wcId = str(row[0]);
            String gender = str(row[1]);
            String boothId = str(row[2]);

            HqsWcItem wc = byWcId.computeIfAbsent(wcId, id -> HqsWcItem.builder()
                    .wcId(id)
                    .gender(gender)
                    .wcBoothList(new ArrayList<>())
                    .build());

            wc.getWcBoothList().add(HqsWcBoothItem.builder()
                    .wcBoothId(boothId)
                    .boothStatus(toBoothStatus(str(row[3])))
                    .build());
        }

        return HqsBoothStatusData.builder()
                .bldgId(request.bldgId())
                .florId(request.florId())
                .wcList(new ArrayList<>(byWcId.values()))
                .build();
    }

    private static int toBoothStatus(String raw) {
        return BoothOccupancy.OCCUPIED.equals(raw) ? 1 : 0;
    }

    /** 빈 문자열을 null 로 — 쿼리의 "필터 없음"과 "빈 값으로 필터"를 구분하기 위함이다. */
    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
