package com.mpole.hana.digitaltwin.command.api;

import com.mpole.hana.digitaltwin.command.dto.CommandMetaResponse;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제어 메타(enum 값·의미·순서) 제공 API.
 * 클라이언트가 제어 유형(TOGGLE/ENUM/RANGE)을 하드코딩하지 않고 이 응답으로
 * 드롭다운·편집폼 전환을 구동한다(EventMeta 와 동일 패턴).
 */
@RestController
@RequestMapping("/api/command/meta")
@RequiredArgsConstructor
@Tag(name = "CommandMeta", description = "제어 enum 메타(값·설명·순서) API")
public class CommandMetaController {

    @Operation(summary = "제어 메타 조회",
            description = "제어 포인트 관리·제어 화면이 사용하는 enum 값 집합과 설명을 {값:설명} map 으로 반환합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<CommandMetaResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success("제어 메타 조회", CommandMetaResponse.build()));
    }
}
