package com.mpole.hana.digitaltwin.monitor.service;

import com.mpole.hana.common.monitor.dto.DependencyStatus;
import com.mpole.hana.common.monitor.dto.LiveStatus;
import com.mpole.hana.common.monitor.dto.MonitorSnapshot;
import com.mpole.hana.common.monitor.dto.ResourceUsage;
import com.mpole.hana.common.monitor.dto.ServiceMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * event / gateway / digitaltwin(self) 3개 서비스의 Spring Boot Actuator를
 * HTTP 폴링해 hana-common의 {@link MonitorSnapshot} DTO로 정규화하는 서비스.
 *
 * <p>각 대상에 대해 {@code /actuator/health}로 생존 상태를, 살아있으면
 * {@code /actuator/metrics/*}로 JVM/프로세스 리소스를, {@code /actuator/info}로
 * PID/시작시각을 수집한다. 한 서비스가 느려도 전체 응답이 지연되지 않도록 짧은
 * connect/read timeout(2초)을 사용한다.
 *
 * <p>또한 actuator를 노출하지 않는 외부 인프라 의존처(DB/Kafka 등)에 대해서는
 * TCP 도달성만 판정해 {@link DependencyStatus} 목록으로 상단 카드용 정보를 조립한다.
 */
@Slf4j
@Service
public class MonitorService {

    /** 리소스 metric 수집 실패 시 double 필드에 채우는 센티넬 값. */
    private static final double SENTINEL_DOUBLE = -1.0;

    /** 네트워크 도달성 TCP connect timeout(ms). */
    private static final int NETWORK_CONNECT_TIMEOUT_MS = 1000;

    private final String eventBaseUrl;
    private final String gatewayBaseUrl;
    private final String digitaltwinBaseUrl;

    /** 외부 인프라 의존처(DB/Kafka 등) 도달성 판정 대상 목록. yml로 override 가능. */
    private final List<DependencyTarget> dependencyTargets;

    private final RestClient restClient;

    public MonitorService(
            @Value("${hdt.monitor.targets.event:http://localhost:8091}") String eventBaseUrl,
            @Value("${hdt.monitor.targets.gateway:http://localhost:8090}") String gatewayBaseUrl,
            @Value("${hdt.monitor.targets.digitaltwin:http://localhost:8085}") String digitaltwinBaseUrl,
            @Value("${hdt.monitor.dependencies}") List<String> dependenciesRaw
    ) {
        this.eventBaseUrl = eventBaseUrl;
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.digitaltwinBaseUrl = digitaltwinBaseUrl;
        this.dependencyTargets = parseDependencyTargets(dependenciesRaw);

        // 한 대상이 느려도 전체가 지연되지 않도록 짧은 timeout 적용 (spring-web 내장, 추가 의존성 없음)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * 3개 대상을 순차 폴링해 스냅샷을 구성한다.
     * actuator 미노출 외부 의존처는 TCP 도달성만 판정해 {@link DependencyStatus} 목록으로 담는다.
     */
    public MonitorSnapshot getSnapshot() {
        List<ServiceMonitor> services = List.of(
                pollService("hana-event", eventBaseUrl),
                pollService("hana-gateway", gatewayBaseUrl),
                pollService("hana-digitaltwin", digitaltwinBaseUrl)
        );
        List<DependencyStatus> dependencies = collectDependencies();
        return MonitorSnapshot.builder()
                .timestamp(OffsetDateTime.now())
                .services(services)
                .dependencies(dependencies)
                .build();
    }

    /**
     * 단일 서비스의 health + 리소스 + PID/시작시각을 폴링한다.
     * health 미응답/예외/비2xx면 DOWN, resource=null, responseTimeMs=null.
     */
    private ServiceMonitor pollService(String serviceName, String baseUrl) {
        long start = System.currentTimeMillis();
        Map<String, Object> health;
        try {
            health = restClient.get()
                    .uri(baseUrl + "/actuator/health")
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            String reason = summarize(e);
            log.info("[monitor] {} health poll 실패: {}", serviceName, reason);
            return ServiceMonitor.builder()
                    .service(serviceName)
                    .status(LiveStatus.DOWN)
                    .responseTimeMs(null)
                    .resource(null)
                    .detail("health 폴링 실패: " + reason)
                    .pid(null)
                    .startedAt(null)
                    .build();
        }

        long responseTimeMs = System.currentTimeMillis() - start;
        LiveStatus status = mapStatus(health);

        // health가 DOWN/UNKNOWN이면 리소스/info 수집을 시도하지 않는다.
        if (status != LiveStatus.UP) {
            return ServiceMonitor.builder()
                    .service(serviceName)
                    .status(status)
                    .responseTimeMs(responseTimeMs)
                    .resource(null)
                    .detail("health status=" + rawStatus(health))
                    .pid(null)
                    .startedAt(null)
                    .build();
        }

        ResourceUsage resource = collectResource(serviceName, baseUrl, health);
        ProcessInfo processInfo = collectProcessInfo(serviceName, baseUrl);
        return ServiceMonitor.builder()
                .service(serviceName)
                .status(LiveStatus.UP)
                .responseTimeMs(responseTimeMs)
                .resource(resource)
                .detail(null)
                .pid(processInfo.pid())
                .startedAt(processInfo.startedAt())
                .build();
    }

    /**
     * health 응답의 status 필드를 LiveStatus로 매핑한다.
     * "UP"→UP, "DOWN"/"OUT_OF_SERVICE"→DOWN, 그 외/파싱불가→UNKNOWN.
     */
    private LiveStatus mapStatus(Map<String, Object> health) {
        String raw = rawStatus(health);
        if (raw == null) {
            return LiveStatus.UNKNOWN;
        }
        return switch (raw.toUpperCase()) {
            case "UP" -> LiveStatus.UP;
            case "DOWN", "OUT_OF_SERVICE" -> LiveStatus.DOWN;
            default -> LiveStatus.UNKNOWN;
        };
    }

    private String rawStatus(Map<String, Object> health) {
        if (health == null) {
            return null;
        }
        Object status = health.get("status");
        return status == null ? null : status.toString();
    }

    /**
     * 살아있는 서비스의 JVM/프로세스 리소스를 수집한다.
     * 개별 metric 수집 실패는 해당 필드만 센티넬(-1.0 / 0)로 두고 서비스는 UP 유지.
     *
     * <p>diskUsage는 이미 확보한 {@code health} 응답의 diskSpace 컴포넌트에서 계산해
     * 불필요한 중복 HTTP 호출을 피한다(show-details:always 전제). 컴포넌트 부재/실패 시 -1.0.
     */
    private ResourceUsage collectResource(String serviceName, String baseUrl, Map<String, Object> health) {
        long heapUsedBytes = (long) metricValue(baseUrl, "jvm.memory.used", 0.0);
        long heapMaxBytes = (long) metricValue(baseUrl, "jvm.memory.max", 0.0);
        double processCpuLoad = metricValue(baseUrl, "process.cpu.usage", SENTINEL_DOUBLE);
        double systemCpuLoad = metricValue(baseUrl, "system.cpu.usage", SENTINEL_DOUBLE);
        int threadCount = (int) metricValue(baseUrl, "jvm.threads.live", 0.0);
        // process.uptime은 초 단위 gauge → ms로 환산
        double uptimeSeconds = metricValue(baseUrl, "process.uptime", 0.0);
        long uptimeMs = uptimeSeconds < 0 ? 0L : (long) (uptimeSeconds * 1000);
        // 서비스가 뜬 호스트의 디스크 사용률(각 서비스 health의 diskSpace 재사용)
        double diskUsage = collectDiskUsage(health);

        return ResourceUsage.builder()
                .heapUsedBytes(heapUsedBytes)
                .heapMaxBytes(heapMaxBytes)
                .processCpuLoad(processCpuLoad)
                .systemCpuLoad(systemCpuLoad)
                .threadCount(threadCount)
                .uptimeMs(uptimeMs)
                .diskUsage(diskUsage)
                .build();
    }

    /**
     * {@code /actuator/info}를 호출해 process.pid / process.startTime을 추출한다.
     * (hana-common PidInfoContributor가 노출한 값 기준.)
     * 수집/파싱 실패는 각 필드를 null로 둔다. 서비스 status에는 영향을 주지 않는다.
     */
    private ProcessInfo collectProcessInfo(String serviceName, String baseUrl) {
        try {
            Map<String, Object> info = restClient.get()
                    .uri(baseUrl + "/actuator/info")
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
            if (info == null) {
                return ProcessInfo.EMPTY;
            }
            Object processObj = info.get("process");
            if (!(processObj instanceof Map<?, ?> process)) {
                return ProcessInfo.EMPTY;
            }
            Long pid = asLong(process.get("pid"));
            OffsetDateTime startedAt = asOffsetDateTime(process.get("startTime"));
            return new ProcessInfo(pid, startedAt);
        } catch (Exception e) {
            log.debug("[monitor] {} info 수집 실패: {}", serviceName, summarize(e));
            return ProcessInfo.EMPTY;
        }
    }

    /**
     * 설정된 외부 인프라 의존처(DB/Kafka 등) 각각에 TCP connect를 시도해
     * 도달성을 판정하고 {@link DependencyStatus} 목록으로 조립한다.
     * 목록이 비었으면 빈 리스트를 반환한다.
     */
    private List<DependencyStatus> collectDependencies() {
        List<DependencyStatus> result = new ArrayList<>();
        for (DependencyTarget target : dependencyTargets) {
            LiveStatus status = isReachable(target.address())
                    ? LiveStatus.UP
                    : LiveStatus.DOWN;
            if (status == LiveStatus.DOWN) {
                log.debug("[monitor] dependency 도달 실패: {} ({})", target.name(), target.displayTarget());
            }
            result.add(DependencyStatus.builder()
                    .name(target.name())
                    .target(target.displayTarget())
                    .status(status)
                    .build());
        }
        return result;
    }

    /**
     * health의 diskSpace 컴포넌트 details.{total,free}로 (total-free)/total 계산.
     * 컴포넌트/필드 부재·total<=0·수집 실패 시 -1.0.
     */
    private double collectDiskUsage(Map<String, Object> health) {
        Map<?, ?> details = nestedMap(health, "components", "diskSpace", "details");
        if (details == null) {
            return SENTINEL_DOUBLE;
        }
        Long total = asLong(details.get("total"));
        Long free = asLong(details.get("free"));
        if (total == null || free == null || total <= 0) {
            return SENTINEL_DOUBLE;
        }
        double used = (double) (total - free) / (double) total;
        // 방어: 음수/1 초과는 센티넬 처리
        if (used < 0 || used > 1.0) {
            return SENTINEL_DOUBLE;
        }
        return used;
    }

    /**
     * 단일 host:port 대상에 TCP connect를 시도해 도달성을 판정한다.
     * 미해석 주소는 먼저 해석 후 시도하고, 실패/타임아웃 시 false.
     */
    private boolean isReachable(InetSocketAddress target) {
        if (target.isUnresolved()) {
            try {
                // target.getHostName()은 "192.168.250.181" 문자열을 반환합니다.
                InetAddress addr = InetAddress.getByName(target.getHostName());
                target = new InetSocketAddress(addr, target.getPort());
            } catch (java.net.UnknownHostException e) {
                return false; // IP 주소 형식이 완전히 잘못되었을 경우
            }
        }

        try (Socket socket = new Socket()) {
            socket.connect(target, NETWORK_CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * {@code /actuator/metrics/{name}} 을 호출해 measurements의 VALUE statistic 값을 추출한다.
     * 널/빈 리스트/예외는 방어하고 {@code fallback}을 반환한다.
     */
    private double metricValue(String baseUrl, String metricName, double fallback) {
        return metricValueInternal(baseUrl + "/actuator/metrics/" + metricName, metricName, fallback);
    }

    private double metricValueInternal(String uri, String label, double fallback) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
            if (body == null) {
                return fallback;
            }
            Object measurementsObj = body.get("measurements");
            if (!(measurementsObj instanceof List<?> measurements) || measurements.isEmpty()) {
                return fallback;
            }
            // statistic=VALUE 인 항목 우선, 없으면 첫 항목 사용
            for (Object m : measurements) {
                if (m instanceof Map<?, ?> measurement) {
                    Object statistic = measurement.get("statistic");
                    Object value = measurement.get("value");
                    if ("VALUE".equals(statistic) && value instanceof Number number) {
                        return number.doubleValue();
                    }
                }
            }
            Object first = measurements.get(0);
            if (first instanceof Map<?, ?> measurement) {
                Object value = measurement.get("value");
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
            }
            return fallback;
        } catch (Exception e) {
            log.debug("[monitor] metric '{}' 수집 실패 ({}): {}", label, uri, summarize(e));
            return fallback;
        }
    }

    // ─────────────────────────────── 파싱 헬퍼 ───────────────────────────────

    /**
     * 콤마 구분 {@code name=host:port} 목록을 {@link DependencyTarget} 리스트로 파싱한다.
     * {@code name=} 이 없으면 host를 name으로 사용한다. 잘못된 항목(포트 파싱 실패 등)은 무시한다.
     */
    private List<DependencyTarget> parseDependencyTargets(List<String> raw) {
        List<DependencyTarget> result = new ArrayList<>();
        if (raw.isEmpty()) {
            return result;
        }
        for (String token : raw) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // name=host:port 형태에서 name 분리 (첫 '='만 기준, host:port의 ':'과 혼동 방지)
            String name = null;
            String hostPort = trimmed;
            int eq = trimmed.indexOf('=');
            if (eq > 0 && eq < trimmed.length() - 1) {
                name = trimmed.substring(0, eq).trim();
                hostPort = trimmed.substring(eq + 1).trim();
            }
            int idx = hostPort.lastIndexOf(':');
            if (idx <= 0 || idx == hostPort.length() - 1) {
                log.warn("[monitor] 잘못된 dependency 무시: {}", trimmed);
                continue;
            }
            String host = hostPort.substring(0, idx);
            String portStr = hostPort.substring(idx + 1);
            try {
                int port = Integer.parseInt(portStr);
                String displayName = (name == null || name.isEmpty()) ? host : name;
                result.add(new DependencyTarget(
                        displayName,
                        InetSocketAddress.createUnresolved(host, port)));
            } catch (NumberFormatException e) {
                log.warn("[monitor] 잘못된 dependency 포트 무시: {}", trimmed);
            }
        }
        return result;
    }

    /** Number/문자열을 Long으로 안전 변환. 실패 시 null. */
    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** ISO-8601 문자열을 OffsetDateTime으로 안전 파싱. 실패/부재 시 null. */
    private OffsetDateTime asOffsetDateTime(Object value) {
        if (value instanceof String s && !s.isBlank()) {
            try {
                return OffsetDateTime.parse(s.trim());
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    /** 중첩 key 경로를 따라 Map을 안전 탐색. 중간에 Map이 아니면 null. */
    private Map<?, ?> nestedMap(Map<String, Object> root, String... keys) {
        Map<?, ?> current = root;
        for (String key : keys) {
            if (current == null) {
                return null;
            }
            Object v = current.get(key);
            if (!(v instanceof Map<?, ?> m)) {
                return null;
            }
            current = m;
        }
        return current;
    }

    private String summarize(Exception e) {
        String msg = e.getMessage();
        String type = e.getClass().getSimpleName();
        return (msg == null || msg.isBlank()) ? type : type + ": " + msg;
    }

    /** info에서 추출한 PID/시작시각 쌍. */
    private record ProcessInfo(Long pid, OffsetDateTime startedAt) {
        static final ProcessInfo EMPTY = new ProcessInfo(null, null);
    }

    /** 파싱된 외부 의존처 대상: 표시명 + TCP 도달성 판정용 미해석 주소. */
    private record DependencyTarget(String name, InetSocketAddress address) {
        /** DependencyStatus.target 표기용 "host:port" 문자열. */
        String displayTarget() {
            return address.getHostString() + ":" + address.getPort();
        }
    }
}
