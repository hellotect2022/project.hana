package com.mpole.hana.digitaltwin.event.api;

import com.mpole.hana.digitaltwin.event.dto.EventMetaResponse;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이벤트 메타(enum 값·의미·순서) 제공 API.
 * 클라이언트가 scope/level/operator/conditionKind/outputChannel/cctvSource/notificationChannel/
 * actionStatus/eventStatus/suppressMode 값을 하드코딩하지 않고 이 응답으로 드롭다운/검증/정렬을
 * 구동한다(SopMeta 와 동일 패턴).
 */
@RestController
@RequestMapping("/api/event/meta")
@RequiredArgsConstructor
@Tag(name = "EventMeta", description = "이벤트 enum 메타(값·설명·순서) API")
public class EventMetaController {

    @Operation(summary = "이벤트 메타 조회", description = "이벤트 규칙/이벤트 화면이 사용하는 enum 값 집합과 설명을 {값:설명} map 으로 반환합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<EventMetaResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success("이벤트 메타 조회", EventMetaResponse.build()));
    }
}
