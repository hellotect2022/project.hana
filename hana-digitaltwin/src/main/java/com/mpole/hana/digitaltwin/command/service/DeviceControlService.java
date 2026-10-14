package com.mpole.hana.digitaltwin.command.service;

import com.mpole.hana.digitaltwin.command.dto.DeviceControlRequest;
import com.mpole.hana.digitaltwin.command.dto.DeviceControlResponse;
import com.mpole.hana.digitaltwin.command.entity.CommandPoint;
import com.mpole.hana.digitaltwin.command.entity.CommandPoint.CommandOption;
import com.mpole.hana.digitaltwin.command.entity.CommandType;
import com.mpole.hana.digitaltwin.command.exception.DeviceControlException;
import com.mpole.hana.digitaltwin.command.port.DeviceControlPort;
import com.mpole.hana.digitaltwin.command.repository.CommandPointRepository;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import com.mpole.hana.digitaltwin.global.security.JwtAuthenticationFilter.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 디바이스 제어 명령 서비스. 제어 포인트를 조회해 ref 코드/값을 확정하고
 * {@link DeviceControlPort} 로 외부(XN) 연계에 명령을 중계한다.
 *
 * <p>감사(audit): 전용 제어 감사 엔티티가 아직 없어 who/what/result 를 로그로 남긴다.
 * (AuditLogService 는 로그인/사용 이력 전용이라 제어 감사 메서드가 없음)</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeviceControlService {

    private final CommandPointRepository commandPointRepository;
    private final DeviceControlPort deviceControlPort;

    public DeviceControlResponse control(DeviceControlRequest req) {
        if (req == null || req.commandPointId() == null) {
            throw new IllegalArgumentException("commandPointId 는 필수입니다.");
        }
        if (req.value() == null || req.value().isBlank()) {
            // 해석은 유형별로 다르다(resolveSendValue) — 여기서는 비었는지만 본다.
            throw new IllegalArgumentException("value 는 필수입니다.");
        }

        CommandPoint cmd = commandPointRepository.findById(req.commandPointId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "제어 포인트를 찾을 수 없습니다. id=" + req.commandPointId()));

        if (Boolean.FALSE.equals(cmd.getActive())) {
            throw new IllegalStateException("비활성 제어 포인트입니다. id=" + req.commandPointId());
        }

        DevicePoint point = cmd.getPoint();
        String refDeviceCode = point != null ? point.getRefDeviceCode() : null;
        String refPointCode = point != null ? point.getRefPointCode() : null;
        if (refDeviceCode == null || refDeviceCode.isBlank() || refPointCode == null || refPointCode.isBlank()) {
            throw new IllegalStateException("제어 포인트에 SI 매핑(ref_device_code/ref_point_code)이 없습니다. commandPointId=" + req.commandPointId());
        }

        String sendValue = resolveSendValue(cmd, req.value());

        boolean success = deviceControlPort.dispatch(refDeviceCode, refPointCode, sendValue);

        // 감사 로그 (who / what / result)
        log.info("[디바이스제어] who={}, commandPointId={}, deviceCode={}, pointCode={}, type={}, input={}, value={}, result={}",
                currentActor(), cmd.getCommandPointId(), refDeviceCode, refPointCode,
                cmd.getCommandType(), req.value(), sendValue, success ? "SUCCESS" : "FAIL");

        // 외부 연계 실패 시 예외로 전파 → GlobalExceptionHandler 가 502 + DEVICE_CONTROL_FAILED 로 응답 자체를 실패 처리
        if (!success) {
            throw new DeviceControlException(String.format(
                    "제어 API 호출 실패 [디바이스제어] commandPointId=%d, deviceCode=%s, pointCode=%s",
                    cmd.getCommandPointId(), refDeviceCode, refPointCode));
        }
        return DeviceControlResponse.ok(sendValue + " 명령 전송 완료");
    }

    /**
     * 요청값 → 실제 전송값 확정. <b>유형마다 해석이 다르다.</b>
     *
     * <pre>
     *   TOGGLE  "1"/"0"/"on"/"off"  → onValue / offValue 로 치환
     *   ENUM    options 의 value 중 하나여야 함 → 그대로
     *   RANGE   숫자 · min~max · step 배수 검증 → 그대로
     * </pre>
     *
     * <p>TOGGLE 경로는 기존 동작과 동일하므로 commandType 을 모르는 기존 클라이언트도 깨지지 않는다.</p>
     */
    private String resolveSendValue(CommandPoint cmd, String rawValue) {
        String v = rawValue.trim();
        CommandType type = cmd.getCommandType() != null ? cmd.getCommandType() : CommandType.TOGGLE;

        return switch (type) {
            case TOGGLE -> {
                if (v.equals("1") || v.equalsIgnoreCase("on")) yield cmd.getOnValue();
                if (v.equals("0") || v.equalsIgnoreCase("off")) yield cmd.getOffValue();
                throw new IllegalArgumentException("TOGGLE 제어는 1/0 만 허용됩니다. value=" + rawValue);
            }
            case ENUM -> {
                List<CommandOption> options = cmd.getOptions();
                if (options == null || options.isEmpty()) {
                    throw new IllegalStateException(
                            "ENUM 제어 포인트에 선택지가 없습니다. commandPointId=" + cmd.getCommandPointId());
                }
                boolean allowed = options.stream()
                        .anyMatch(o -> o != null && o.value() != null && o.value().trim().equals(v));
                if (!allowed) {
                    throw new IllegalArgumentException("허용되지 않은 선택값입니다. value=" + rawValue);
                }
                yield v;
            }
            case RANGE -> {
                double num;
                try {
                    num = Double.parseDouble(v);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("RANGE 제어는 숫자만 허용됩니다. value=" + rawValue);
                }
                Double min = cmd.getMinValue();
                Double max = cmd.getMaxValue();
                if (min == null || max == null) {
                    throw new IllegalStateException(
                            "RANGE 제어 포인트에 범위(min/max)가 없습니다. commandPointId=" + cmd.getCommandPointId());
                }
                if (num < min || num > max) {
                    throw new IllegalArgumentException(
                            "허용 범위를 벗어났습니다. value=" + rawValue + ", 허용=" + min + "~" + max);
                }
                Double step = cmd.getStepValue();
                if (step != null && step > 0 && !isStepAligned(num, min, step)) {
                    throw new IllegalArgumentException(
                            "입력 단위에 맞지 않습니다. value=" + rawValue + ", step=" + step);
                }
                yield v;
            }
        };
    }

    /**
     * min 을 기준으로 step 배수인지 판정한다.
     * <p>부동소수 오차(0.1 + 0.2 != 0.3)로 정상값이 거부되지 않도록 허용 오차를 둔다.</p>
     */
    private static boolean isStepAligned(double value, double min, double step) {
        double steps = (value - min) / step;
        return Math.abs(steps - Math.round(steps)) < 1e-9;
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return "anonymous";
        }
        if (auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.loginId();
        }
        return auth.getName();
    }
}
