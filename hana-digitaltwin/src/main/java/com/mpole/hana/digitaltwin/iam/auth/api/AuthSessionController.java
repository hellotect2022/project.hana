package com.mpole.hana.digitaltwin.iam.auth.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.global.security.JwtAuthenticationFilter;
import com.mpole.hana.digitaltwin.iam.auth.dto.ActiveSessionsResponse;
import com.mpole.hana.digitaltwin.iam.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/sessions")
@RequiredArgsConstructor
public class AuthSessionController {

    private final AuthService authService;

    @GetMapping
    public ResponseEntity<ApiResponse<ActiveSessionsResponse>> getActiveSessions(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("활성 세션 목록", authService.getActiveSessions(principal.loginId()))
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            HttpServletRequest request
    ) {
        authService.logout(principal.loginId(), principal.deviceId(), request);
        return ResponseEntity.ok(ApiResponse.success("로그아웃 완료"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            HttpServletRequest request
    ) {
        authService.logoutAllDevices(principal.loginId(),request);
        return ResponseEntity.ok(ApiResponse.success("모든 기기 로그아웃 완료"));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> revokeDevice(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PathVariable String deviceId
    ) {
        authService.revokeDevice(principal.loginId(), deviceId);
        return ResponseEntity.ok(ApiResponse.success("기기 세션 종료 완료"));
    }
}
