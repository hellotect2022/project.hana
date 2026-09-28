package com.mpole.hana.digitaltwin.integration.emblaze.hqs.v2;

import com.mpole.hana.digitaltwin.command.dto.DeviceControlRequest;
import com.mpole.hana.digitaltwin.command.service.DeviceControlService;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenControlData;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenControlRequest;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenGroupItem;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenListData;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenListRequest;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 전동롤스크린 (v2) — 장비 그룹 기준.
 *
 * <p><b>요청·응답 DTO 는 v1 것을 그대로 쓴다.</b> 새로 만들면 "같은 응답"이 아니게 된다.
 * 바뀌는 것은 한 줄이 무엇을 뜻하는가 하나다.</p>
 *
 * <pre>
 *   v1  groupId = 모터 device_id (20161)   groupName = F003_M001      한 줄 = 모터 1대
 *   v2  groupId = group_code (RS.F003.Z001) groupName = 롤스크린_3층_서쪽  한 줄 = 구역 1개
 * </pre>
 *
 * <p>기존 {@code HqsRollscreenService} 는 건드리지 않는다. 두 경로를 나란히 두고
 * 비교한 뒤에 전환 여부를 정한다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HqsGroupRollscreenService {

    /**
     * 값이 없을 때 내보낼 개방률.
     * <p>명세의 {@code openPercent} 는 정수라 "모름"을 표현할 코드가 없다.
     * 수집 전이거나 통신이 끊긴 구역은 0(닫힘)으로 나간다 — v1 과 같은 처리다.</p>
     */
    private static final int UNKNOWN_OPEN_PERCENT = 0;

    private final HqsGroupRollscreenRepository hqsGroupRollscreenRepository;
    private final DeviceControlService deviceControlService;

    /* ════════════════════════════════════════════════════════════════
     * 조회
     * ════════════════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public HqsRollscreenListData getRollscreenList(HqsRollscreenListRequest request) {
        List<Object[]> floorRows = hqsGroupRollscreenRepository.resolveFloor(request.florId());
        if (floorRows.isEmpty()) {
            // 없는 층이면 빈 목록을 준다 — 요청한 florId 를 그대로 돌려줘 무엇을 물었는지 남긴다.
            log.warn("[롤스크린v2] 알 수 없는 florId={}", request.florId());
            return HqsRollscreenListData.builder()
                    .florId(request.florId())
                    .florName(null)
                    .groupList(List.of())
                    .build();
        }

        Object[] floor = floorRows.get(0);
        Integer floorNum = floor[0] == null ? null : ((Number) floor[0]).intValue();
        String floorName = floor[1] == null ? null : floor[1].toString();

        List<HqsRollscreenGroupItem> groups = new ArrayList<>();
        for (Object[] row : hqsGroupRollscreenRepository.findGroupsByFloor(floorName)) {
            groups.add(HqsRollscreenGroupItem.builder()
                    .groupId(str(row[0]))                       // group_code
                    .groupName(str(row[1]))
                    .openPercent(toOpenPercent((BigDecimal) row[2]))
                    .build());
        }

        if (groups.isEmpty()) {
            // 층은 있는데 구역이 없다 = 그 층 그룹이 비활성이거나 모터 배정 전이다.
            // 빈 배열이 나가는 이유를 로그에 남겨 두지 않으면 원인을 찾느라 시간을 쓴다.
            log.info("[롤스크린v2] florId={}({}) 활성 구역 없음 — 그룹 비활성이거나 모터 미배정",
                    request.florId(), floorName);
        }

        return HqsRollscreenListData.builder()
                .florId(floorNum == null ? request.florId() : String.valueOf(floorNum))
                .florName(floorName)
                .groupList(groups)
                .build();
    }

    /* ════════════════════════════════════════════════════════════════
     * 제어
     * ════════════════════════════════════════════════════════════════ */

    /**
     * 구역 개방률 제어.
     *
     * <p>{@code groupId} 는 {@code group_code}({@code RS.F003.Z001})다. 그 그룹의
     * {@code CONTROLLER} 멤버가 명령을 받는다 — 상태를 내는 모터와는 다른 장비다.</p>
     *
     * <p>실행은 기존 {@code DeviceControlService} 를 그대로 탄다 — 범위·step 검증과
     * XN 중계, 감사 로그가 이미 거기 있다. 거기서 나온 예외는 <b>그대로 올려보낸다</b>.
     * {@code HqsApiExceptionHandler} 가 사유를 응답 message 에 싣는다.</p>
     *
     * @throws IllegalArgumentException 층·구역을 못 찾았거나 제어 포인트가 없을 때
     */
    @Transactional
    public HqsRollscreenControlData control(HqsRollscreenControlRequest request) {
        String floorName = resolveFloorName(request.florId());
        String groupCode = request.groupId().trim();

        Tuple target = hqsGroupRollscreenRepository.findControlPoint(groupCode, floorName)
                .orElseThrow(() -> new IllegalArgumentException(describeControlFailure(groupCode, floorName, request.florId())));

        Long commandPointId = ((Number) target.get("command_point_id")).longValue();
        String sendValue = String.valueOf(toPosition(request.openPercent()));

        // 실패는 예외로 전파한다 — 삼켜서 "success" 를 주면 HQS 는 움직인 줄 안다.
        deviceControlService.control(new DeviceControlRequest(commandPointId, sendValue));

        log.info("[롤스크린제어v2] florId={} group={} openPercent={} → pos={} commandPointId={}",
                request.florId(), groupCode, request.openPercent(), sendValue, commandPointId);
        return HqsRollscreenControlData.success();
    }

    /**
     * 제어 대상을 못 찾은 이유를 가른다.
     *
     * <p>"그룹이 없다"와 "제어 포인트가 등록되지 않았다"는 전혀 다른 문제다. 앞은 요청이
     * 잘못된 것이고, 뒤는 우리 데이터가 덜 찬 것이다. 같은 메시지로 뭉뚱그리면 상대가
     * 자기 요청을 의심하며 시간을 버린다.</p>
     */
    private String describeControlFailure(String groupCode, String floorName, String florId) {
        List<Object[]> rows = hqsGroupRollscreenRepository.findGroupByCode(groupCode);
        if (rows.isEmpty()) {
            return "구역을 찾을 수 없습니다. groupId=" + groupCode;
        }
        String groupFloor = str(rows.get(0)[2]);
        if (groupFloor != null && !groupFloor.equals(floorName)) {
            return "구역의 층이 다릅니다. groupId=" + groupCode
                    + " 요청층=" + florId + " 실제층=" + groupFloor;
        }
        return "제어 포인트가 등록되지 않은 구역입니다. groupId=" + groupCode
                + " — 구역 제어기의 pos 제어 포인트를 먼저 등록해야 합니다.";
    }

    /* ════════════════════════════════════════════════════════════════
     * 변환
     * ════════════════════════════════════════════════════════════════ */

    /**
     * HQS {@code openPercent} → 제어 포인트 {@code pos}. <b>값을 뒤집는다.</b>
     *
     * <pre>
     *   HQS openPercent   100 = 완전열림     0 = 닫힘
     *   제어 pos          최상단 = 0(개방)   최하단 = 100(차광)
     * </pre>
     *
     * <p>뒤집지 않으면 <b>"완전열림" 명령이 스크린을 끝까지 내린다.</b>
     * 20 단위라 뒤집어도 step 정렬이 유지된다(100→0, 60→40, 0→100).</p>
     */
    private static int toPosition(int openPercent) {
        return 100 - openPercent;
    }

    /**
     * 구역 모터들의 <b>최빈</b> {@code pos-st} → HQS {@code openPercent}. <b>값을 뒤집는다.</b>
     *
     * <pre>
     *   우리 pos-st       최상단 = 0(개방)   최하단 = 100(차광)
     *   HQS openPercent   100 = 완전열림     0 = 닫힘
     * </pre>
     *
     * <p>그대로 흘리면 <b>완전히 열린 구역이 "닫힘"으로 나간다.</b> 현재 대부분의 값이
     * {@code 0.0}(= 완전 개방)이라 반전을 빠뜨리면 HQS 화면 전체가 닫힘으로 보인다.</p>
     *
     * <p>평균이 아니라 최빈값을 받는 이유는 리포지토리 주석 참고 — 제어가 20 단위라
     * 평균은 되돌려 보낼 수 없는 값을 만든다.</p>
     */
    private static int toOpenPercent(BigDecimal modePos) {
        if (modePos == null) {
            return UNKNOWN_OPEN_PERCENT;
        }
        long open = 100L - Math.round(modePos.doubleValue());
        return (int) Math.max(0, Math.min(100, open));
    }

    /** {@code florId}(층번호 또는 층이름) → 층 이름({@code 3F}). 그룹은 층을 이 형식으로 들고 있다. */
    private String resolveFloorName(String florId) {
        List<Object[]> rows = hqsGroupRollscreenRepository.resolveFloor(florId);
        if (rows.isEmpty() || rows.get(0)[1] == null) {
            throw new IllegalArgumentException("층을 찾을 수 없습니다. florId=" + florId);
        }
        return rows.get(0)[1].toString();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
