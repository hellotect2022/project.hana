package com.mpole.hana.digitaltwin.integration.emblaze.hqs;

import com.mpole.hana.digitaltwin.integration.emblaze.config.HqsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicReference;

/**
 * HQS 호출용 access token 공급.
 *
 * <p>명세상 {@code Authorization: Bearer <token>} 은 "login 으로 발급 받은 access token" 이다.
 * 그런데 <b>login API 명세를 아직 받지 못해</b> 자동 발급을 구현할 수 없다(경로·자격증명·
 * 요청/응답 형식·만료시간 전부 미상). 그래서 지금은 설정값으로 주입한 토큰을 그대로 쓴다.</p>
 *
 * <h3>login 이 붙으면 고칠 곳</h3>
 * <p>{@link #refresh()} 하나다. 로그인을 호출해 새 토큰을 {@link #cached} 에 넣으면
 * {@link HqsClient} 는 손대지 않아도 된다 — 이미 인증 실패 시 refresh 후 1회 재시도한다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HqsTokenProvider {

    private final HqsProperties props;

    /** 현재 토큰. 설정값으로 초기화되고, login 구현 후에는 refresh 가 갱신한다. */
    private final AtomicReference<String> cached = new AtomicReference<>();

    /** 없으면 null — 호출부가 헤더를 생략하고 경고를 남긴다. */
    public String currentToken() {
        String token = cached.get();
        if (StringUtils.hasText(token)) {
            return token;
        }
        String configured = props.getAccessToken();
        if (!StringUtils.hasText(configured)) {
            return null;
        }
        cached.compareAndSet(null, configured.trim());
        return cached.get();
    }

    /**
     * 토큰 재발급. <b>login API 명세를 받기 전까지는 아무것도 하지 않는다.</b>
     *
     * @return 갱신에 성공했으면 true. 지금은 항상 false 라 호출부가 재시도를 건너뛴다.
     */
    public boolean refresh() {
        log.error("HQS 토큰이 만료·무효인데 재발급 경로가 없다 — login API 명세 확보 후 "
                + "HqsTokenProvider.refresh() 구현 필요. 임시로는 "
                + "hdt.integration.hqs.access-token 을 새 토큰으로 교체해야 한다.");
        return false;

        // ─── login 명세를 받으면 이런 모양이 된다 ───────────────────────────
        // HqsLoginResponse res = hqsClient.login(props.getLoginId(), props.getLoginPassword());
        // if (res == null || !res.isSuccess() || !StringUtils.hasText(res.accessToken())) {
        //     return false;
        // }
        // cached.set(res.accessToken());
        // return true;
    }

    /** 설정을 바꿔 넣었을 때 캐시를 비운다(운영 중 토큰 교체용). */
    public void invalidate() {
        cached.set(null);
    }
}
