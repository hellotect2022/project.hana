package com.mpole.hana.digitaltwin.integration.emblaze.hqs.v2;

import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsApiResponse;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenControlData;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenControlRequest;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenListData;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenListRequest;
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
 * HQS 전동롤스크린 API v2 — 장비 그룹 기준.
 *
 * <p>요청·응답 형식은 v1 과 같다. 바뀌는 것은 <b>한 줄이 무엇을 뜻하는가</b>다.</p>
 *
 * <pre>
 *   v1  한 줄 = 모터 1대    groupId = device_id      (615대가 나열된다)
 *   v2  한 줄 = 구역 1개    groupId = group_code     (3층 19개 구역)
 * </pre>
 *
 * <p>v1 이 "그룹"이라는 이름으로 모터를 하나씩 내보낸 것은 구역 개념이 DB 에 없었기
 * 때문이다. 이제 {@code tbl_device_group} 이 있으므로 HQS 명세가 원래 기대한 대로
 * 구역 단위로 나간다.</p>
 *
 * <p><b>v1 은 손대지 않았다.</b> 둘을 나란히 띄워 비교한 뒤 전환 여부를 정한다.</p>
 *
 * <p>보안은 별도 설정이 필요 없다 — {@code SecurityConfig} 의
 * {@code /api/v1/hqs/**  hasAnyRole("ADMIN","HQS")} 를 그대로 상속받는다.</p>
 */
@RestController
@RequestMapping("/api/v1/hqs/rollscreen/v2")
@RequiredArgsConstructor
@Tag(name = "HQS Rollscreen (v2)", description = "전동롤스크린 구역 조회·제어 — 장비 그룹 기준. 형식은 v1 과 동일")
public class HqsGroupRollscreenController {

    private final HqsGroupRollscreenService hqsGroupRollscreenService;

    @PostMapping("/rollscreens/list")
    @Operation(summary = "롤스크린 구역 목록 (V2)",
            description = "한 줄이 모터가 아니라 구역이다. groupId 는 group_code(RS.F003.Z001), "
                    + "openPercent 는 그 구역 MONITOR 멤버들의 pos-st 평균을 뒤집은 값이다. "
                    + "비활성 그룹은 빠진다 — 현재 3층만 활성이라 다른 층은 빈 목록이 나간다.")
    public ResponseEntity<HqsApiResponse<HqsRollscreenListData>> getRollscreenList(
            @Valid @RequestBody HqsRollscreenListRequest request) {
        HqsRollscreenListData data = hqsGroupRollscreenService.getRollscreenList(request);
        return ResponseEntity.ok(HqsApiResponse.success(data));
    }

    @PostMapping("/control")
    @Operation(summary = "롤스크린 구역 제어 (V2)",
            description = "groupId 는 group_code 다. 그 구역의 CONTROLLER 멤버가 명령을 받는다. "
                    + "openPercent 100=완전열림이며 제어 포인트 pos 로는 뒤집혀 나간다(100-x). "
                    + "현재 3층 19개 구역 중 RS.F003.Z000(전체)에만 제어 포인트가 등록돼 있어 "
                    + "나머지 구역은 사유가 담긴 400 이 나간다.")
    public ResponseEntity<HqsApiResponse<HqsRollscreenControlData>> control(
            @Valid @RequestBody HqsRollscreenControlRequest request) {
        HqsRollscreenControlData data = hqsGroupRollscreenService.control(request);
        return ResponseEntity.ok(HqsApiResponse.success(data));
    }
}
