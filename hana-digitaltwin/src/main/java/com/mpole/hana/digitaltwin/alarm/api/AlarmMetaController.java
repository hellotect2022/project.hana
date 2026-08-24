package com.mpole.hana.digitaltwin.alarm.api;

import com.mpole.hana.digitaltwin.alarm.dto.AlarmMetaResponse;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알람 메타(enum 값·의미·순서) 제공 API.
 * 클라이언트가 scope/level/operator/conditionKind/displayType/outputChannel/cctvSource 값을
 * 하드코딩하지 않고 이 응답으로 드롭다운/검증/정렬을 구동한다(SopMeta 와 동일 패턴).
 */
@RestController
@RequestMapping("/api/alarm/meta")
@RequiredArgsConstructor
@Tag(name = "AlarmMeta", description = "알람 enum 메타(값·설명·순서) API")
public class AlarmMetaController {

    @Operation(summary = "알람 메타 조회", description = "알람 정책/이벤트 화면이 사용하는 enum 값 집합과 설명을 {값:설명} map 으로 반환합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<AlarmMetaResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success("알람 메타 조회", AlarmMetaResponse.build()));
    }
}
