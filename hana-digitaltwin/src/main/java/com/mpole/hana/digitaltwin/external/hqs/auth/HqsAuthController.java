package com.mpole.hana.digitaltwin.external.hqs.auth;

import com.mpole.hana.digitaltwin.external.hqs.auth.dto.HqsLoginRequest;
import com.mpole.hana.digitaltwin.external.hqs.common.HqsResponseCode;
import com.mpole.hana.digitaltwin.external.hqs.common.HqsApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HQS 로그인 API — HQS 명세 URI 준수.
 *
 * <p>여기서 받은 JWT 를 HQS 가 이후 호출의 {@code Authorization: Bearer <token>} 에 넣는다.
 * 토큰의 권한은 계정에 붙은 역할을 그대로 따르므로, {@code HQS} 역할 계정으로 로그인하면
 * HQS 연동 API 만 통과하고 관리자 API 는 통과하지 못한다.</p>
 *
 * <p>응답 형식은 다른 HQS 연동 API 와 같은 {@link HqsApiResponse} 봉투다
 * ({@code code}/{@code message}/{@code timestamp}/{@code data}). {@code data} 에 JWT 가 담긴다.</p>
 *
 * <p>실패 응답을 컨트롤러에서 직접 만드는 이유 — {@code HqsApiExceptionHandler} 가
 * 이 패키지를 커버하긴 하지만, 인증 실패는 <b>예외 종류에 따라 HQS 코드가 달라야</b> 한다
 * (210 자격증명 불일치 / 220 사용 불가 계정 / 290 처리 오류). 전역 처리에 맡기면 전부 999 가 된다.</p>
 */
@RestController
@RequestMapping("/api/v1/hqs/login")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "HQS-Auth", description = "HQS 연동 — 로그인(JWT 발급)")
public class HqsAuthController {

    private final HqsAuthService hqsAuthService;

    //region swagger 🔄 HQS 로그인 명세서
    @Operation(
            summary = "HQS 로그인(JWT 발급)",
            description = """
                    HQS 가 우리 API 를 호출하기 위한 access token 을 발급합니다.

                    **요청**
                    · `clientId` = 계정의 login_id · `clientValue` = 비밀번호

                    **응답**
                    · `data` = JWT. 이후 호출의 `Authorization: Bearer <data>` 에 넣습니다.

                    **권한** — 토큰 권한은 계정 역할을 따릅니다.
                    · `HQS` 역할 → HQS 연동 API 만 호출 가능
                    · `ADMIN` 역할 → 전체 통과

                    refresh token 은 발급하지 않습니다. 만료되면 이 API 를 다시 호출하면 됩니다.
                    """
    )
    //endregion
    @PostMapping("/external")
    public ResponseEntity<HqsApiResponse<String>> loginExternal(
            @RequestHeader(value = "X-trxId", required = false) String trxId,
            @Valid @RequestBody HqsLoginRequest request) {

        try {
            String token = hqsAuthService.issueAccessToken(request);
            return ResponseEntity.ok(HqsApiResponse.success(token));

        } catch (IllegalArgumentException e) {
            // 자격증명 불일치 — 어느 쪽이 틀렸는지 밝히지 않는다.
            log.warn("HQS 로그인 실패 trxId={} clientId={}: {}", trxId, request.clientId(), e.getMessage());
            return ResponseEntity.ok(HqsApiResponse.error(HqsResponseCode.AUTH_INVALID_INFO));

        } catch (IllegalStateException e) {
            // 계정 잠김·비활성 — 자격증명은 맞을 수 있으나 쓸 수 없는 상태
            log.warn("HQS 로그인 거부 trxId={} clientId={}: {}", trxId, request.clientId(), e.getMessage());
            return ResponseEntity.ok(HqsApiResponse.error(HqsResponseCode.AUTH_INFO_NOT_EXIST));

        } catch (Exception e) {
            log.error("HQS 로그인 오류 trxId={} clientId={}", trxId, request.clientId(), e);
            return ResponseEntity.ok(HqsApiResponse.error(HqsResponseCode.AUTH_FAILED_PROCESS));
        }
    }
}
