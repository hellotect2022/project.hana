package com.mpole.hana.digitaltwin.external.hqs.wc;

import com.mpole.hana.digitaltwin.external.hqs.common.HqsApiResponse;
import com.mpole.hana.digitaltwin.external.hqs.wc.dto.HqsBoothStatusData;
import com.mpole.hana.digitaltwin.external.hqs.wc.dto.HqsBoothStatusRequest;
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
 * HQS 화장실 API — 장비 그룹 기준.
 *
 * <p>경로의 {@code v1} 은 <b>HQS 가 정한 외부 경로</b>지 우리 버전이 아니다. 그래서 내용을
 * 바꿔도 경로는 그대로다.</p>
 *
 * <p>한동안 이 로직이 {@code /api/v1/hqs/v2/boothStatus} 에 따로 떠 있었다. 구현이 둘이던
 * 시기에 같은 요청으로 응답을 대조하기 위해서였고, 8개 필터 조합에서 72/72 행이 일치하는 것을
 * 확인한 뒤 <b>구현을 이쪽으로 합치고 임시 경로를 없앴다</b>. zone 계층 대신
 * {@code tbl_device_group} 에서 부스를 읽는 것이 예전 구현과의 차이다.</p>
 *
 * <p>보안은 별도 설정이 필요 없다 — {@code SecurityConfig} 의
 * {@code /api/v1/hqs/**  hasAnyRole("ADMIN","HQS")} 를 그대로 상속받는다.</p>
 */
@RestController
@RequestMapping("/api/v1/hqs")
@RequiredArgsConstructor
@Tag(name = "HQS WC", description = "화장실 부스 재실 현황 — 장비 그룹 기준")
public class HqsBoothController {

    private final HqsBoothService hqsBoothService;

    @PostMapping("/boothStatus")
    @Operation(summary = "부스 재실 현황",
            description = "부스를 zone(depth 1) 이 아니라 "
                    + "tbl_device_group(group_type='WC_BOOTH') 에서 읽고, 재실값은 그룹의 "
                    + "MONITOR 멤버에서 occ 태그로 집계한다. 층(florId)은 "
                    + "장비 배치(placement → zone → floor)로 판별한다.")
    public ResponseEntity<HqsApiResponse<HqsBoothStatusData>> getBoothStatus(
            @Valid @RequestBody HqsBoothStatusRequest request) {
        HqsBoothStatusData data = hqsBoothService.getBoothStatusData(request);
        return ResponseEntity.ok(HqsApiResponse.success(data));
    }
}
