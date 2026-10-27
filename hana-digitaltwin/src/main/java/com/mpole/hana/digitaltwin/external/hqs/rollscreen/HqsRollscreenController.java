package com.mpole.hana.digitaltwin.external.hqs.rollscreen;

import com.mpole.hana.digitaltwin.external.hqs.common.HqsApiResponse;
import com.mpole.hana.digitaltwin.external.hqs.rollscreen.dto.HqsRollscreenControlData;
import com.mpole.hana.digitaltwin.external.hqs.rollscreen.dto.HqsRollscreenControlRequest;
import com.mpole.hana.digitaltwin.external.hqs.rollscreen.dto.HqsRollscreenListData;
import com.mpole.hana.digitaltwin.external.hqs.rollscreen.dto.HqsRollscreenListRequest;
import com.mpole.hana.digitaltwin.external.hqs.rollscreen.dto.HqsRollscreenStopRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HQS 전동롤스크린 API — 장비 그룹 기준.
 *
 * <p>경로의 {@code v1} 은 <b>HQS 가 정한 외부 경로</b>지 우리 버전이 아니다.
 * 요청·응답 형식은 예전 구현과 같고, 바뀐 것은 <b>한 줄이 무엇을 뜻하는가</b>다.</p>
 *
 * <pre>
 *   이전  한 줄 = 모터 1대    groupId = device_id      (615대가 나열된다)
 *   현재  한 줄 = 구역 1개    groupId = group_code     (3층 19개 구역)
 * </pre>
 *
 * <p>예전 구현이 "그룹"이라는 이름으로 모터를 하나씩 내보낸 것은 구역 개념이 DB 에 없었기
 * 때문이다. 이제 {@code tbl_device_group} 이 있으므로 HQS 명세가 원래 기대한 대로
 * 구역 단위로 나간다.</p>
 *
 * <p>보안은 별도 설정이 필요 없다 — {@code SecurityConfig} 의
 * {@code /api/v1/hqs/**  hasAnyRole("ADMIN","HQS")} 를 그대로 상속받는다.</p>
 */
@RestController
@RequestMapping("/api/v1/hqs/rollscreen")
@RequiredArgsConstructor
@Tag(name = "HQS Rollscreen", description = "전동롤스크린 구역 조회·제어 — 장비 그룹 기준")
public class HqsRollscreenController {

    private final HqsRollscreenService hqsRollscreenService;

    @PostMapping("/rollscreens/list")
    @Operation(summary = "롤스크린 구역 목록",
            description = "한 줄이 모터가 아니라 구역이다. groupId 는 group_code(RS.F003.Z001), "
                    + "openPercent 는 그 구역 CONTROLLER(구역 제어기)의 pos 를 뒤집은 값이다. "
                    + "pos 는 제어 출력이라 한 번도 제어된 적 없는 구역은 값이 없고 0 으로 나간다 "
                    + "— '닫힘'이 아니라 '모름'이라는 뜻이다. "
                    + "비활성 그룹은 빠진다 — 현재 3층만 활성이라 다른 층은 빈 목록이 나간다.")
    public ResponseEntity<HqsApiResponse<HqsRollscreenListData>> getRollscreenList(
            @Valid @RequestBody HqsRollscreenListRequest request) {
        HqsRollscreenListData data = hqsRollscreenService.getRollscreenList(request);
        return ResponseEntity.ok(HqsApiResponse.success(data));
    }

    @PostMapping("/control")
    @Operation(summary = "롤스크린 구역 제어",
            description = "groupId 는 group_code 다. 그 구역의 CONTROLLER 멤버가 명령을 받는다. "
                    + "openPercent 100=완전열림이며 제어 포인트 pos 로는 뒤집혀 나간다(100-x). "
                    + "현재 3층 19개 구역 중 RS.F003.Z000(전체)에만 제어 포인트가 등록돼 있어 "
                    + "나머지 구역은 사유가 담긴 400 이 나간다.")
    public ResponseEntity<HqsApiResponse<HqsRollscreenControlData>> control(
            @Valid @RequestBody HqsRollscreenControlRequest request) {
        HqsRollscreenControlData data = hqsRollscreenService.control(request);
        return ResponseEntity.ok(HqsApiResponse.success(data));
    }

    @PostMapping("/stop")
    @Operation(summary = "롤스크린 구역 정지",
            description = "움직이는 롤스크린을 그 자리에서 멈춘다(2026-10-26). 요청은 /control 에서 openPercent 만 뺀 모양이다. "
                    + "groupId 는 group_code(RS.F003.Z013)이고, 그 구역 CONTROLLER 의 stop 제어 포인트가 명령을 받는다. "
                    + "보내는 값은 그 제어 포인트 선택지의 첫 값이다(현재 1). "
                    + "구역 없음·층 불일치·정지 제어 포인트 미등록은 사유가 담긴 400 이 나간다. "
                    + "정지는 목표값을 바꾸지 않으므로 목록의 openPercent 는 마지막으로 보낸 목표값 그대로다.")
    public ResponseEntity<HqsApiResponse<HqsRollscreenControlData>> stop(
            @Valid @RequestBody HqsRollscreenStopRequest request) {
        HqsRollscreenControlData data = hqsRollscreenService.stop(request);
        return ResponseEntity.ok(HqsApiResponse.success(data));
    }
}
