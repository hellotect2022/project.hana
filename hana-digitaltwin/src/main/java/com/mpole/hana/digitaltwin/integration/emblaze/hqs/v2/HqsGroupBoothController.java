package com.mpole.hana.digitaltwin.integration.emblaze.hqs.v2;

import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsApiResponse;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc.dto.HqsBoothStatusData;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc.dto.HqsBoothStatusRequest;
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
 * HQS 화장실 API v2 — 장비 그룹 기준.
 *
 * <p>요청·응답이 v1({@code POST /api/v1/hqs/boothStatus})과 완전히 같다. 경로만 다르다.
 * zone 계층 대신 {@code tbl_device_group} 에서 부스를 읽는 것이 유일한 차이다.</p>
 *
 * <p><b>v1 은 손대지 않았다.</b> 둘을 나란히 띄워 같은 요청에 같은 응답이 나오는지
 * 대조한 뒤에 전환 여부를 정한다.</p>
 *
 * <p>이 프로젝트의 v2 관례는 "같은 컨트롤러에 {@code /v2/} 메서드 추가"지만
 * ({@code DeviceByCategoryController}), 그러면 기존 파일을 수정하게 되므로 새 컨트롤러로
 * 둔다. {@code /api/device} 를 세 컨트롤러가 나눠 쓰는 선례가 이미 있다.</p>
 *
 * <p>보안은 별도 설정이 필요 없다 — {@code SecurityConfig} 의
 * {@code /api/v1/hqs/**  hasAnyRole("ADMIN","HQS")} 를 그대로 상속받는다.</p>
 */
@RestController
@RequestMapping("/api/v1/hqs/v2")
@RequiredArgsConstructor
@Tag(name = "HQS WC (v2)", description = "화장실 부스 재실 현황 — 장비 그룹 기준. 응답은 v1 과 동일")
public class HqsGroupBoothController {

    private final HqsGroupBoothService hqsGroupBoothService;

    @PostMapping("/boothStatus")
    @Operation(summary = "부스 재실 현황 (V2)",
            description = "v1 과 응답 형식이 같다. 부스를 zone(depth 1) 이 아니라 "
                    + "tbl_device_group(group_type='WC_BOOTH') 에서 읽고, 재실값은 그룹의 "
                    + "MONITOR 멤버에서 occ 태그로 집계한다. 층(florId)은 v1 과 동일하게 "
                    + "장비 배치(placement → zone → floor)로 판별한다.")
    public ResponseEntity<HqsApiResponse<HqsBoothStatusData>> getBoothStatus(
            @Valid @RequestBody HqsBoothStatusRequest request) {
        HqsBoothStatusData data = hqsGroupBoothService.getBoothStatusData(request);
        return ResponseEntity.ok(HqsApiResponse.success(data));
    }
}
