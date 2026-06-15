package com.mpole.hana.digitaltwin.iam.auth.api;

import com.mpole.hana.digitaltwin.audit.service.AuditLogService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.global.security.JwtAuthenticationFilter;
import com.mpole.hana.digitaltwin.iam.auth.dto.ChangePasswordRequest;
import com.mpole.hana.digitaltwin.iam.auth.dto.LoginRequest;
import com.mpole.hana.digitaltwin.iam.auth.dto.LoginResponse;
import com.mpole.hana.digitaltwin.iam.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 관련 API")
public class AuthController {

    private final AuthService authService;
    private final AuditLogService auditLogService;

    //region 🔑 [로그인 API]
    @Operation(
            summary = "로그인",
            description = "loginId/password 로 로그인하고 토큰을 발급받는다.",
            // 💡 파라미터가 아니라 여기에 requestBody 설정을 통째로 박아버립니다!
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginRequest.class),
                            examples = @ExampleObject(
                                    name = "로그인 기본값",
                                    value = """
                                            {
                                              "loginId": "admin",
                                              "password": "1q2w3e"
                                            }
                                            """
                            )
                    )
            )
    )
    //endregion
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(
                    ApiResponse.success("로그인 성공", authService.login(request, httpRequest))
            );
        }catch (Exception e) {
            auditLogService.recordLoginFailure(request.loginId(), httpRequest, e.getMessage());
            throw e;
        }
    }


    //region 🔄 토큰 갱신 명세서
    @Operation(
            summary = "토큰 갱신 (Refresh)",
            description = "만료된 Access Token을 Header에 담아 호출하면 새로운 토큰을 발급받는다.",
            // 💡 파라미터가 아니라 여기에 requestBody 설정을 통째로 박아버립니다!
            // 💡 시큐리티 인증(JWT)이 필요한 API임을 스웨거에 명시 (자물쇠 표시 아이콘 활성화)
            security = @SecurityRequirement(name = "bearerAuth")
    )
    //endregion
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "토큰 갱신 성공",
                        authService.refreshToken(principal.loginId(), principal.deviceId(), httpRequest)
                )
        );
    }

    //region [비밀번호 변경]
    @Operation(
            summary = "비밀번호 변경",
            description = "현재 로그인한 사용자의 비밀번호를 변경한다.",
            security = @SecurityRequirement(name = "bearerAuth"), // 자물쇠 활성화
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChangePasswordRequest.class),
                            examples = @ExampleObject(
                                    name = "비밀번호 변경 예시",
                                    value = """
                                            {
                                              "currentPassword": "1q2w3e",
                                              "newPassword": "newPassword123!"
                                            }
                                            """
                            )
                    )
            )
    )
    //endregion
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(principal.loginId(), request);
        return ResponseEntity.ok(ApiResponse.success("비밀번호 변경 완료"));
    }

    //region [계정잠금해제]
    @Operation(
            summary = "계정 잠금 해제",
            description = "잠긴 계정을 loginId 기반으로 강제 해제한다.",
            parameters = {
                    @Parameter(
                            name = "loginId",
                            description = "잠금 해제할 사용자의 로그인 ID",
                            in = ParameterIn.PATH,
                            required = true,
                            example = "admin" // URL 경로에 대입될 예시값
                    )
            }
    )
    //endregion
    @PostMapping("/unlock/{loginId}")
    public ResponseEntity<ApiResponse<Void>> unlockAccount(@PathVariable String loginId) {
        authService.unlockAccount(loginId);
        return ResponseEntity.ok(ApiResponse.success("계정 잠금 해제 완료"));
    }

    //region [내정보 조회]
    @Operation(
            summary = "내 정보 조회 (Me)",
            description = "Header의 토큰을 기반으로 현재 로그인한 사용자의 정보를 조회한다.",
            security = @SecurityRequirement(name = "bearerAuth") // 자물쇠 활성화
    )
    //endregion
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LoginResponse.UserInfo>> me(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("현재 사용자", authService.getUserInfo(principal.loginId()))
        );
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth Service is running");
    }

}
