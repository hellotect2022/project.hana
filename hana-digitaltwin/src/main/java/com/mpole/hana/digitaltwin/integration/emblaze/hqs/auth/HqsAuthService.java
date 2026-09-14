package com.mpole.hana.digitaltwin.integration.emblaze.hqs.auth;

import com.mpole.hana.digitaltwin.global.security.JwtTokenProvider;
import com.mpole.hana.digitaltwin.iam.auth.service.UserLockService;
import com.mpole.hana.digitaltwin.iam.user.entity.User;
import com.mpole.hana.digitaltwin.iam.user.repository.UserRepository;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.auth.dto.HqsLoginRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * HQS 로그인 — access token 만 발급한다.
 *
 * <h3>왜 {@code AuthService.login()} 을 그대로 안 쓰는가</h3>
 * <p>사람용 로그인은 호출 1회마다 <b>기기 세션 1건</b>을 만든다(deviceId 생성 →
 * {@code tbl_refresh_token} 에 행 추가 → 활성 기기 목록 조회). HQS 는 토큰이 만료될 때마다
 * 기계적으로 재로그인하므로 그 경로를 타면 <b>세션 행이 무한정 쌓인다.</b>
 * 게다가 HQS 명세의 응답에는 refresh token 자리가 없다({@code data} 에 JWT 하나뿐).</p>
 *
 * <p>그래서 <b>인증 규칙(계정 조회·상태 검증·비밀번호 대조·실패 카운트)은 동일하게</b> 두되
 * 세션·refresh token 생성만 뺀 경로를 따로 둔다. 권한은 계정에 붙은 역할을 그대로 쓰므로
 * {@code user-hqs} 계정에 {@code HQS} 역할을 주면 그 토큰이 HQS 권한을 갖는다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HqsAuthService {

    /**
     * HQS 토큰의 {@code deviceId} 고정값.
     * <p>사람용 세션과 섞이지 않도록 상수로 둔다 — 세션 목록·강제 로그아웃 기능이
     * 이 토큰을 기기로 오인하지 않게 하기 위함이다.</p>
     */
    public static final String HQS_DEVICE_ID = "hqs-external";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserLockService userLockService;

    /**
     * 자격증명을 검증하고 access token 을 만든다.
     *
     * <p>실패 사유(계정 없음/비밀번호 불일치)를 구분해 응답하지 않는다 —
     * 어느 쪽인지 알려주면 유효한 clientId 를 찾는 단서가 된다.</p>
     *
     * @return JWT access token
     * @throws IllegalArgumentException 자격증명 불일치
     * @throws IllegalStateException    계정 잠김·비활성
     */
    @Transactional
    public String issueAccessToken(HqsLoginRequest request) {
        User user = userRepository.findByLoginId(request.clientId())
                .orElseThrow(() -> new IllegalArgumentException("clientId 또는 clientValue 가 올바르지 않습니다."));

        if (!user.getActive()) {
            throw new IllegalStateException("비활성화된 계정입니다.");
        }

        if (!passwordEncoder.matches(request.clientValue(), user.getPasswordHash())) {
            // 사람용 로그인과 같은 실패 카운트를 탄다.
            //userLockService.handleLoginFailure(user);
            throw new IllegalArgumentException("clientId 또는 clientValue 가 올바르지 않습니다.");
        }

        user.resetFailedAttempts();

        List<String> roles = user.roleNames();
        if (roles.isEmpty()) {
            // 토큰은 발급되지만 어떤 API 도 통과하지 못한다 — 원인을 찾기 어려우니 미리 남긴다.
            log.warn("HQS 로그인: 역할이 없는 계정 clientId={} — 모든 요청이 403 이 된다", user.getLoginId());
        }

        String token = jwtTokenProvider.generateAccessToken(user.getLoginId(), HQS_DEVICE_ID, roles);
        log.info("HQS 로그인 성공 clientId={} roles={}", user.getLoginId(), roles);
        return token;
    }
}
