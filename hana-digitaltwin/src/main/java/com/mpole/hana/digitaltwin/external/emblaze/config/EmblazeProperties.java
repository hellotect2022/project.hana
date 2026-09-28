package com.mpole.hana.digitaltwin.external.emblaze.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * emblaze RabbitMQ 수신 연동 설정. {@code hdt.integration.emblaze.*} 로 덮어쓴다.
 * <p>{@code enabled} 기본 false 로 전체 RabbitMQ config/listener 를 게이팅한다(기본 비활성).
 * 자격증명은 원본 하드코딩값 대신 빈 문자열 placeholder 로 두고, 운영은 env/yml 로 주입(Loop4).</p>
 */
@ConfigurationProperties(prefix = "hdt.integration.emblaze")
@Getter
@Setter
public class EmblazeProperties {

    /** true 일 때만 RabbitMQ config/listener 가 기동한다(원본 emblaze.isUse 대응). */
    private boolean enabled = false;

    private Rabbit rabbit = new Rabbit();

    @Getter
    @Setter
    public static class Rabbit {
        /** monitoring/control 공유 host */
        private String host = "";
        private Endpoint monitoring = new Endpoint();
        private Endpoint control = new Endpoint();
        /** 브로커 접속 실패 시 재연결 백오프 정책(일정 시간 후 STOP) */
        private Recovery recovery = new Recovery();
    }

    /**
     * 리스너 재연결(recovery) 백오프. 브로커가 계속 안 뜨면 무한 재시도로 리소스를 먹지 않도록
     * {@code maxElapsedMs} 경과 후 재시도를 중단(STOP)하고 컨슈머를 멈춘다.
     */
    @Getter
    @Setter
    public static class Recovery {
        /** 첫 재시도 간격(ms) */
        private long initialIntervalMs = 3000L;
        /** 간격 증가 배수 */
        private double multiplier = 2.0;
        /** 재시도 간격 상한(ms) */
        private long maxIntervalMs = 30000L;
        /** 이 시간(ms) 동안 계속 실패하면 재시도 중단 → 컨슈머 정지(기본 60초) */
        private long maxElapsedMs = 60000L;
    }

    @Getter
    @Setter
    public static class Endpoint {
        private int port;
        /** 운영은 env/yml 주입(Loop4) */
        private String username = "";
        /** 운영은 env/yml 주입(Loop4) */
        private String password = "";
        private String virtualHost = "/";
    }
}
