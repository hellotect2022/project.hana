package com.mpole.hana.digitaltwin.iam.auth.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.global.security.JwtAuthenticationFilter;
import com.mpole.hana.digitaltwin.iam.auth.dto.ActiveSessionsResponse;
import com.mpole.hana.digitaltwin.iam.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/sessions")
@RequiredArgsConstructor
@Tag(name = "Auth Session", description = "사용자 세션 및 기기 관리 API")
public class AuthSessionController {

    private final AuthService authService;

    //region 1.[활성 세션 목록 조회]
    @Operation(
            summary = "활성 세션 목록 조회",
            description = "현재 로그인한 사용자의 활성화된 모든 세션(기기 정보 등) 목록을 조회한다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    //endregion
    @GetMapping
    public ResponseEntity<ApiResponse<ActiveSessionsResponse>> getActiveSessions(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("활성 세션 목록", authService.getActiveSessions(principal.loginId()))
        );
    }

    //region 2.[사용자 로그아웃]
    @Operation(
            summary = "사용자 로그아웃",
            description = "현재 요청한 기기의 세션을 만료시키고 로그아웃 처리한다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    //endregion
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            HttpServletRequest request
    ) {
        authService.logout(principal.loginId(), principal.deviceId(), request);
        return ResponseEntity.ok(ApiResponse.success("로그아웃 완료"));
    }

    //region 3.[특정 사용자의 모든 기기 활성 세션 종료]
    @Operation(
            summary = "모든 기기 로그아웃",
            description = "현재 로그인한 사용자의 모든 기기 및 웹 세션을 강제로 만료시킨다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    //endregion
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            HttpServletRequest request
    ) {
        authService.logoutAllDevices(principal.loginId(),request);
        return ResponseEntity.ok(ApiResponse.success("모든 기기 로그아웃 완료"));
    }

    //region 4.[특정 기기 활성 세션 삭제]
    @Operation(
            summary = "특정 기기 세션 종료",
            description = "전달받은 deviceId 값을 가진 특정 기기의 세션을 강제로 만료시킨다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters = {
                    @Parameter(
                            name = "deviceId",
                            description = "종료할 대상 기기의 고유 ID",
                            in = ParameterIn.PATH,
                            required = true,
                            example = "DESKTOP-PC-01"
                    )
            }
    )
    //endregion
    @DeleteMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> revokeDevice(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PathVariable String deviceId
    ) {
        authService.revokeDevice(principal.loginId(), deviceId);
        return ResponseEntity.ok(ApiResponse.success("기기 세션 종료 완료"));
    }
}
