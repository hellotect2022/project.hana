package com.mpole.hana.digitaltwin.iam.auth.service;

import com.mpole.hana.common.util.RequestHeaderUtils;
import com.mpole.hana.digitaltwin.audit.service.AuditLogService;
import com.mpole.hana.digitaltwin.global.security.JwtTokenProvider;
import com.mpole.hana.digitaltwin.iam.auth.dto.ActiveSessionsResponse;
import com.mpole.hana.digitaltwin.iam.auth.dto.ChangePasswordRequest;
import com.mpole.hana.digitaltwin.iam.auth.dto.LoginRequest;
import com.mpole.hana.digitaltwin.iam.auth.dto.LoginResponse;
import com.mpole.hana.digitaltwin.iam.user.entity.RefreshToken;
import com.mpole.hana.digitaltwin.iam.user.entity.User;
import com.mpole.hana.digitaltwin.iam.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserLockService userLockService;
    private final AuditLogService auditLogService;

    @Value("${password.change-period-days:90}")
    private int passwordChangePeriodDays;

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new IllegalArgumentException("사용자명 또는 비밀번호가 올바르지 않습니다."));

        validateUserStatus(user);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            userLockService.handleLoginFailure(user);
            throw new IllegalArgumentException("사용자명 또는 비밀번호가 올바르지 않습니다.");
        }

        user.resetFailedAttempts();
        String deviceId = UUID.randomUUID().toString();
        String deviceName = RequestHeaderUtils.getInstance().resolveDeviceName(httpRequest);
        String deviceType = RequestHeaderUtils.getInstance().resolveDeviceType(httpRequest);
        String ipAddress = RequestHeaderUtils.getInstance().getClientIp(httpRequest);

        List<String> roles = user.roleNames();

        String accessToken = jwtTokenProvider.generateAccessToken(user.getLoginId(), deviceId, roles);
        String refreshToken = refreshTokenService.createRefreshToken(
                user.getLoginId(),
                deviceId,
                deviceName,
                deviceType,
                ipAddress
        );

        ActiveSessionsResponse activeSessions = refreshTokenService.getActiveSessions(user.getLoginId());

        // 성공 이력 로그 저장
        auditLogService.recordLoginSuccess(user,httpRequest);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .userInfo(toUserInfo(user))
                .passwordChangeRequired(user.isPasswordChangeRequired(passwordChangePeriodDays))
                .daysUntilPasswordExpiry(calculateDaysUntilPasswordExpiry(user))
                .currentDeviceId(deviceId)
                .activeDevices(activeSessions.sessions().stream()
                        .map(session -> LoginResponse.ActiveDevice.builder()
                                .deviceId(session.deviceId())
                                .deviceName(session.deviceName())
                                .deviceType(session.deviceType())
                                .ipAddress(session.ipAddress())
                                .lastUsedAt(String.valueOf(session.lastUsedAt()))
                                .current(deviceId.equals(session.deviceId()))
                                .build())
                        .toList())
                .build();
    }

    @Transactional
    public LoginResponse refreshToken(String loginId, String deviceId, HttpServletRequest httpRequest) {
        RefreshToken token = refreshTokenService.validateRefreshToken(loginId, deviceId, RequestHeaderUtils.getInstance().getClientIp(httpRequest));

        User user = userRepository.findByLoginId(token.getLoginId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        validateUserStatus(user);

        List<String> roles = user.roleNames();

        String accessToken = jwtTokenProvider.generateAccessToken(user.getLoginId(), deviceId, roles);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(token.getToken())
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .userInfo(toUserInfo(user))
                .passwordChangeRequired(user.isPasswordChangeRequired(passwordChangePeriodDays))
                .daysUntilPasswordExpiry(calculateDaysUntilPasswordExpiry(user))
                .currentDeviceId(deviceId)
                .build();
    }

    @Transactional
    public void logout(String loginId, String deviceId, HttpServletRequest request) {

        // 로그아웃 감시 로그
        auditLogService.recordLogout(loginId,request);

        refreshTokenService.deleteRefreshToken(loginId, deviceId);
    }

    @Transactional
    public void logoutAllDevices(String loginId, HttpServletRequest request) {
        auditLogService.recordLogout(loginId, request);
        refreshTokenService.deleteAllRefreshTokens(loginId);
    }

    @Transactional
    public void revokeDevice(String loginId, String deviceId) {
        refreshTokenService.revokeDevice(loginId, deviceId);
    }

    public ActiveSessionsResponse getActiveSessions(String loginId) {
        return refreshTokenService.getActiveSessions(loginId);
    }

    public LoginResponse.UserInfo getUserInfo(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return toUserInfo(user);
    }

    @Transactional
    public void changePassword(String loginId, ChangePasswordRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
        }

        if (request.currentPassword().equals(request.newPassword())) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));

        refreshTokenService.deleteAllRefreshTokens(loginId);
    }

    @Transactional
    public void unlockAccount(String loginId) {
        userLockService.unlockAccount(loginId);
    }

    private void validateUserStatus(User user) {
        if (!user.getAccountNonLocked()) {
            throw new IllegalStateException("계정이 잠겨있습니다.");
        }
        if (!user.getActive()) {
            throw new IllegalStateException("비활성화된 계정입니다.");
        }
    }

    private LoginResponse.UserInfo toUserInfo(User user) {
        return LoginResponse.UserInfo.builder()
                .loginId(user.getLoginId())
                .email(user.getEmail())
                .name(user.getUsername())
                .roles(user.roleNames())
                .build();
    }

    private Integer calculateDaysUntilPasswordExpiry(User user) {
        if (user.getLastPasswordChangeDate() == null) {
            return 0;
        }

        OffsetDateTime expiryDate = user.getLastPasswordChangeDate().plusDays(passwordChangePeriodDays);
        long days = ChronoUnit.DAYS.between(OffsetDateTime.now(), expiryDate);

        return (int) Math.max(days, 0);
    }
}
