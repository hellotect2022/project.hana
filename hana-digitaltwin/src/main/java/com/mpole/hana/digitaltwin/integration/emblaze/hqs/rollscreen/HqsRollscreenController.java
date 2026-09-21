package com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen;

import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsApiResponse;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenListData;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto.HqsRollscreenListRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HQS 전동롤스크린 조회 API.
 *
 * <p>인증은 {@code SecurityConfig} 의 {@code /api/v1/hqs/**} 규칙이 처리한다
 * ({@code ADMIN} 또는 {@code HQS} 역할). 예외 응답은 {@code HqsApiExceptionHandler} 가
 * 이 패키지를 포함해 HQS 봉투로 변환한다.</p>
 */
@RestController
@RequestMapping("/api/v1/hqs/rollscreen")
@RequiredArgsConstructor
@Tag(name = "HQS-Rollscreen", description = "HQS 연동 — 전동롤스크린 조회")
public class HqsRollscreenController {

    private final HqsRollscreenService hqsRollscreenService;

    //region swagger 🔄 전동롤스크린 목록 조회 명세서
    @Operation(
            summary = "층별 전동롤스크린 목록 조회",
            description = """
                    한 층의 전동롤스크린과 개방률을 반환합니다.

                    **매핑**
                    · `groupId` = 장비의 `device_id` · `groupName` = `device_name`
                    · `openPercent` = `100 - pos-st`

                    **개방률 방향 주의** — 우리 `pos-st` 는 최상단이 0(개방)·최하단이 100(차광)이고,
                    HQS `openPercent` 는 100이 완전열림·0이 닫힘이라 **값을 뒤집어** 내보냅니다.

                    **florId** — 층 번호(`10`)와 층 이름(`10F`) 둘 다 받습니다.
                    없는 층이면 `groupList` 가 빈 배열로 나갑니다.

                    **대상은 구역에 배치된 장비뿐입니다.** 배치되지 않은 롤스크린은 층을 알 수 없어
                    목록에 나오지 않습니다(현재 2,413대 중 615대만 배치 — 4F·10F·14F).

                    **bldgId** — 대상 건물이 하나뿐이라 검증하지 않습니다.

                    값을 한 번도 받지 못한 스크린은 `openPercent` 가 `0` 으로 나갑니다
                    (명세에 "데이터 없음"을 표현할 코드가 없습니다).
                    """
    )
    //endregion
    @PostMapping("/rollscreens/list")
    public ResponseEntity<HqsApiResponse<HqsRollscreenListData>> getRollscreenList(
            @RequestHeader(value = "X-trxId", required = false) String trxId,
            @Valid @RequestBody HqsRollscreenListRequest request) {
        HqsRollscreenListData data = hqsRollscreenService.getRollscreenList(request);
        return ResponseEntity.ok(HqsApiResponse.success(data));
    }
}
