package com.mpole.hana.common.monitor.info;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 현재 프로세스의 PID와 시작 시각을 {@code /actuator/info} 에 노출한다.
 * <p>
 * {@code info.process.pid} 는 항상 노출되고,
 * {@code info.process.startTime} 은 플랫폼에서 시작 시각을 제공하는 경우에만 ISO-8601 문자열로 노출된다.
 * ({@link ProcessHandle#info()} 의 startInstant 는 플랫폼에 따라 비어 있을 수 있다.)
 */
public class PidInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        long pid = ProcessHandle.current().pid();

        Map<String, Object> process = new LinkedHashMap<>();
        process.put("pid", pid);

        Optional<Instant> startInstant = ProcessHandle.current().info().startInstant();
        startInstant.ifPresent(instant -> process.put("startTime", instant.toString()));

        builder.withDetail("process", process);
    }
}
