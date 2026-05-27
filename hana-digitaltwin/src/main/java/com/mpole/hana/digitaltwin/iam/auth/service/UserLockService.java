package com.mpole.hana.digitaltwin.iam.auth.service;

import com.mpole.hana.digitaltwin.iam.user.entity.User;
import com.mpole.hana.digitaltwin.iam.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLockService {

    private final UserRepository userRepo;
    private static final int MAX_FAILED_ATTEMPTS = 1000000;

    /**
     * 로그인 실패 처리 (독립적인 새로운 트랜잭션)
     * REQUIRES_NEW: 부모 트랜잭션과 무관하게 즉시 커밋
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleLoginFailure(User user) {
        log.debug("로그인 실패 처리 시작: {}, 현재 실패 횟수: {}", user.getLoginId(), user.getFailedLoginAttempts());

        user.incrementFailedAttempts();

        if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
            user.lockAccount();
            log.warn("⚠️ 계정 잠금: {} (로그인 실패 {}회)", user.getLoginId(), user.getFailedLoginAttempts());
        } else {
            log.info("로그인 실패 횟수 증가: {} ({}회)", user.getLoginId(), user.getFailedLoginAttempts());
        }

        User savedUser = userRepo.save(user);

        log.debug("DB 저장 완료: failedLoginAttempts={}, accountNonLocked={}",
                savedUser.getFailedLoginAttempts(), savedUser.getAccountNonLocked());
    }

    /**
     * 계정 잠금 해제
     */
    @Transactional
    public void unlockAccount(String loginId) {
        User user = userRepo.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        user.unlockAccount();
        userRepo.save(user);

        log.info("✅ 계정 잠금 해제: {}", loginId);
    }
}
