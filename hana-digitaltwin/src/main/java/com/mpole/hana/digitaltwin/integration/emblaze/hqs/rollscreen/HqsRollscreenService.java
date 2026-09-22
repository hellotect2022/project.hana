package com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen;

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
    private final DeviceControlService deviceControlService;

    @Transactional(readOnly = true)
    public HqsRollscreenListData getRollscreenList(HqsRollscreenListRequest request) {
        List<Object[]> floorRows = rollscreenRepository.resolveFloor(request.florId());
        if (floorRows.isEmpty()) {
            // 없는 층이면 빈 목록을 준다 — 요청한 florId 를 그대로 돌려줘 무엇을 물었는지 남긴다.
            log.warn("전동롤스크린 조회: 알 수 없는 florId={}", request.florId());
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
                .florId(floorNum == null ? request.florId() : String.valueOf(floorNum))
                .florName(floorName)
                .groupList(groups)
                .build();
    }

    /* ════════════════════════════════════════════════════════════════
     * 제어
     * ════════════════════════════════════════════════════════════════ */

    /**
     * 롤스크린 개방률 제어 — <b>제어기 1대</b>.
     *
     * <p>대상은 조회와 다른 장비다 — 상태는 모터({@code MRS}), 제어는 구역 제어기
     * ({@code MRS-CONTROL})가 받는다. 둘을 잇는 관계가 DB 에 없으므로 제어기를 직접 지목한다.</p>
     *
     * <p>{@code groupId} 는 제어기의 {@code device_name} 이고 유일하므로 대상은 항상 1대다.
     * {@code florId} 도 같이 검증한다 — HQS 가 보낸 층과 실제 배치된 층이 다르면 제어하지 않는다
     * (엉뚱한 층을 움직이는 것보다 거절이 낫다).</p>
     *
     * <p>실행은 기존 {@code DeviceControlService} 를 그대로 탄다 — 범위·step 검증과
     * XN 중계, 감사 로그가 이미 거기 있다. 여기서 다시 만들지 않는다. 거기서 나온 예외는
     * <b>그대로 올려보낸다</b> — {@code HqsApiExceptionHandler} 가 사유를 응답 message 에 싣는다.</p>
     *
     * @throws IllegalArgumentException 층·제어기를 못 찾았거나 값이 허용 범위·단위를 벗어났을 때
     * @throws DeviceControlException   XN 연계가 명령을 받지 못했을 때
     */
    @Transactional
    public HqsRollscreenControlData control(HqsRollscreenControlRequest request) {
        Integer floorNum = resolveFloorNum(request.florId());
        String groupName = request.groupId().trim();

        Tuple target = rollscreenRepository.findControlPoint(floorNum, groupName)
                .orElseThrow(() -> new IllegalArgumentException("제어 가능한 롤스크린이 없습니다. groupId=" + groupName + " florId=" + request.florId()));

        Long commandPointId = ((Number) target.get("command_point_id")).longValue();

        String sendValue = String.valueOf(toPosition(request.openPercent()));

        // 실패는 예외로 전파한다 — 삼켜서 "success" 를 주면 HQS 는 움직인 줄 안다.
        deviceControlService.control(new DeviceControlRequest(commandPointId, sendValue));

        log.info("[롤스크린제어] florId={} group={} openPercent={} → pos={} commandPointId={}",
                request.florId(), groupName, request.openPercent(), sendValue, commandPointId);
        return HqsRollscreenControlData.success();
    }

    /**
     * HQS {@code openPercent} → 제어 포인트 {@code pos}. <b>값을 뒤집는다.</b>
     *
     * <pre>
     *   HQS openPercent   100 = 완전열림     0 = 닫힘
     *   제어 pos          최상단 = 0(개방)   최하단 = 100(차광)
     * </pre>
     *
     * <p>조회({@code pos-st})와 같은 방향이다. 뒤집지 않으면 <b>"완전열림" 명령이
     * 스크린을 끝까지 내린다.</b></p>
     *
     * <p>20 단위라 뒤집어도 step 정렬이 유지된다(100→0, 60→40, 0→100).</p>
     */
    private static int toPosition(int openPercent) {
        return 100 - openPercent;
    }

    /** {@code florId}(층번호 또는 층이름) → 층 번호. */
    private Integer resolveFloorNum(String florId) {
        List<Object[]> rows = rollscreenRepository.resolveFloor(florId);
        if (rows.isEmpty() || rows.get(0)[0] == null) {
            throw new IllegalArgumentException("층을 찾을 수 없습니다. florId=" + florId);
        }
        return ((Number) rows.get(0)[0]).intValue();
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
