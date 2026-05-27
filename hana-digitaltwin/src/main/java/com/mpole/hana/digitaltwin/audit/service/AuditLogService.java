package com.mpole.hana.digitaltwin.audit.service;

import com.mpole.hana.common.util.RequestHeaderUtils;
import com.mpole.hana.digitaltwin.audit.dto.*;
import com.mpole.hana.digitaltwin.audit.entity.AccessLog;
import com.mpole.hana.digitaltwin.audit.entity.AccessLogType;
import com.mpole.hana.digitaltwin.audit.entity.UsageLog;
import com.mpole.hana.digitaltwin.audit.repository.AccessLogRepository;
import com.mpole.hana.digitaltwin.audit.repository.UsageLogRepository;
import com.mpole.hana.digitaltwin.iam.user.entity.User;
import com.mpole.hana.digitaltwin.iam.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.springframework.util.StringUtils.truncate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AccessLogRepository accessLogRepository;
    private final UsageLogRepository usageLogRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginSuccess(User user, HttpServletRequest request) {
        accessLogRepository.save(AccessLog.builder()
                .logType(AccessLogType.LOGIN_SUCCESS)
                .loginId(user.getLoginId())
                .username(user.getUsername())
                .userAgent(truncate(request.getHeader("User-Agent"), 512))
                .clientIp(RequestHeaderUtils.getInstance().getClientIp(request))
                .build());
    }

    /** 로그인 실패 — 비밀번호 오류, 계정 잠금 등 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(String loginId, HttpServletRequest request, String detail) {
        accessLogRepository.save(AccessLog.builder()
                .logType(AccessLogType.LOGIN_FAIL)
                .loginId(loginId != null ? loginId : "")
                .userAgent(truncate(request.getHeader("User-Agent"), 512))
                .clientIp(RequestHeaderUtils.getInstance().getClientIp(request))
                .detail(truncate(detail, 2000))
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogout(String loginId, HttpServletRequest request) {
        String username = userRepository.findByLoginId(loginId)
                .map(User::getUsername)
                .orElse(null);

        accessLogRepository.save(AccessLog.builder()
                .logType(AccessLogType.LOGOUT)
                .loginId(loginId)
                .username(username)
                .userAgent(truncate(request.getHeader("User-Agent"), 512))
                .clientIp(RequestHeaderUtils.getInstance().getClientIp(request))
                .build());
    }

    @Transactional
    public void recordUsage(String loginId, UsageLogCreateRequest request, HttpServletRequest httpRequest) {
        usageLogRepository.save(UsageLog.builder()
                .loginId(loginId)
                .menuLabel(request.menuLabel())
                .userAgent(truncate(httpRequest.getHeader("User-Agent"), 512))
                .build());
    }

    public PageAuditResponse<AccessLogItemResponse> getAccessLogs(
            LocalDate from, LocalDate to, int page, int size
    ) {
        Page<AccessLog> result = accessLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                startOfDay(from), endOfDay(to), PageRequest.of(page, size)
        );
        return new PageAuditResponse<>(
                result.getContent().stream().map(AccessLogItemResponse::from).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    public PageAuditResponse<UsageLogItemResponse> getUsageLogs(
            LocalDate from, LocalDate to, int page, int size
    ) {
        Page<UsageLog> result = usageLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                startOfDay(from), endOfDay(to), PageRequest.of(page, size)
        );
        return new PageAuditResponse<>(
                result.getContent().stream().map(UsageLogItemResponse::from).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    public AuditStatisticsResponse getStatistics(LocalDate from, LocalDate to) {
        OffsetDateTime fromDt = startOfDay(from);
        OffsetDateTime toDt = endOfDay(to);

        long loginSuccessTotal = accessLogRepository.countByLogTypeAndCreatedAtBetween(
                AccessLogType.LOGIN_SUCCESS, fromDt, toDt);

        long loginFailTotal = accessLogRepository.countByLogTypeAndCreatedAtBetween(
                AccessLogType.LOGIN_FAIL, fromDt, toDt);

        long logoutTotal = accessLogRepository.countByLogTypeAndCreatedAtBetween(
                AccessLogType.LOGOUT, fromDt, toDt);

        long usageTotal = usageLogRepository.countByCreatedAtBetween(fromDt, toDt);

        LocalDate today = LocalDate.now();

        OffsetDateTime todayStart = startOfDay(today);
        OffsetDateTime todayEnd = endOfDay(today);

        long loginSuccessToday = accessLogRepository.countByLogTypeAndCreatedAtBetween(
                AccessLogType.LOGIN_SUCCESS, todayStart, todayEnd);

        long loginFailToday = accessLogRepository.countByLogTypeAndCreatedAtBetween(
                AccessLogType.LOGIN_FAIL, todayStart, todayEnd);
        long usageToday = usageLogRepository.countByCreatedAtBetween(todayStart, todayEnd);
        Map<LocalDate, Long> loginByDay = toDayCountMap(
                accessLogRepository.countByDayAndType("LOGIN_SUCCESS", fromDt, toDt));
        Map<LocalDate, Long> visitByDay = toDayCountMap(
                usageLogRepository.countUsageByDay(fromDt, toDt));
        List<AuditStatisticsResponse.DailyPoint> daily = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            daily.add(new AuditStatisticsResponse.DailyPoint(
                    d.toString(),
                    loginByDay.getOrDefault(d, 0L),
                    visitByDay.getOrDefault(d, 0L)
            ));
        }
        List<AuditStatisticsResponse.MenuCount> topMenus = usageLogRepository
                .countTopMenus(fromDt, toDt, PageRequest.of(0, 8))
                .stream()
                .map(row -> new AuditStatisticsResponse.MenuCount(
                        row[0] != null ? row[0].toString() : "(기타)",
                        ((Number) row[1]).longValue()
                ))
                .toList();
        long[] hourBuckets = new long[24];
        for (Object[] row : usageLogRepository.countUsageByHour(fromDt, toDt)) {
            int hour = ((Number) row[0]).intValue();
            if (hour >= 0 && hour < 24) {
                hourBuckets[hour] = ((Number) row[1]).longValue();
            }
        }
        List<AuditStatisticsResponse.HourCount> usageByHour = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            usageByHour.add(new AuditStatisticsResponse.HourCount(hour, hourBuckets[hour]));
        }
        return new AuditStatisticsResponse(
                new AuditStatisticsResponse.Summary(
                        loginSuccessTotal, loginFailTotal, logoutTotal, usageTotal,
                        loginSuccessToday, usageToday, loginFailToday
                ),
                daily,
                topMenus,
                usageByHour
        );
    }

    private Map<LocalDate, Long> toDayCountMap(List<Object[]> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate day = switch (row[0]) {
                case Date sqlDate -> sqlDate.toLocalDate();
                case LocalDate ld -> ld;
                default -> null;
            };
            if (day != null) {
                map.put(day, ((Number) row[1]).longValue());
            }
        }
        return map;
    }

    private OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private OffsetDateTime endOfDay(LocalDate date) {
        return date.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
