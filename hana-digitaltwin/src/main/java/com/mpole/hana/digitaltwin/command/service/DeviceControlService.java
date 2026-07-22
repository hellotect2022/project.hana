package com.mpole.hana.digitaltwin.command.service;

import com.mpole.hana.digitaltwin.command.dto.DeviceControlRequest;
import com.mpole.hana.digitaltwin.command.dto.DeviceControlResponse;
import com.mpole.hana.digitaltwin.command.entity.CommandPoint;
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
            throw new IllegalArgumentException("value(on/off) 는 필수입니다.");
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
        if (refDeviceCode == null || refDeviceCode.isBlank()
                || refPointCode == null || refPointCode.isBlank()) {
            throw new IllegalStateException(
                    "제어 포인트에 SI 매핑(ref_device_code/ref_point_code)이 없습니다. commandPointId="
                            + req.commandPointId());
        }

        boolean on = resolveOn(req.value());
        String sendValue = on ? cmd.getOnValue() : cmd.getOffValue();

        boolean success = deviceControlPort.dispatch(refDeviceCode, refPointCode, sendValue);

        // 감사 로그 (who / what / result)
        log.info("[디바이스제어] who={}, commandPointId={}, deviceCode={}, pointCode={}, command={}, value={}, result={}",
                currentActor(), cmd.getCommandPointId(), refDeviceCode, refPointCode,
                on ? "ON" : "OFF", sendValue, success ? "SUCCESS" : "FAIL");

        return success
                ? DeviceControlResponse.ok((on ? "ON" : "OFF") + " 명령 전송 완료")
                : DeviceControlResponse.fail("외부 연계 전송 실패");
    }

    private boolean resolveOn(String value) {
        String v = value.trim();
        if (v.equalsIgnoreCase("on")) {
            return true;
        }
        if (v.equalsIgnoreCase("off")) {
            return false;
        }
        throw new IllegalArgumentException("value 는 on/off 만 허용됩니다. value=" + value);
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
