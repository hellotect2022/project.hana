package com.mpole.hana.gateway.config;

import io.r2dbc.mssql.MssqlConnectionConfiguration;
import io.r2dbc.mssql.MssqlConnectionFactory;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Duration;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

@Configuration
public class DatabaseConfig {

    // MSSQL 설정값 주입
    @Value("${storage.mssql.driver}") String mssqlDriver;
    @Value("${storage.mssql.host}") String mssqlHost;
    @Value("${storage.mssql.port}") int mssqlPort;
    @Value("${storage.mssql.user}") String mssqlUser;
    @Value("${storage.mssql.password}") String mssqlPassword;
    @Value("${storage.mssql.database}") String mssqlDatabase;
    /**
     * 커서(sp_cursorfetch) 사용 여부. 기본 false — 대용량 뷰 풀스캔이 주 용도라 커서가 불리하다.
     * 소량 조회 위주 환경으로 바뀌면 yml 에서 true 로 올릴 수 있다.
     */
    @Value("${storage.mssql.prefer-cursored-execution:false}") boolean mssqlPreferCursoredExecution;

    // Postgres 설정값 주입
    @Value("${storage.postgres.driver}") String pgDriver;
    @Value("${storage.postgres.host}") String pgHost;
    @Value("${storage.postgres.port}") int pgPort;
    @Value("${storage.postgres.user}") String pgUser;
    @Value("${storage.postgres.password}") String pgPassword;
    @Value("${storage.postgres.database}") String pgDatabase;
    @Value("${storage.postgres.schema}") String pgSchema;

    /**
     * SI 측 MSSQL(외부 뷰 조회 전용) 커넥션 팩토리.
     *
     * <p><b>왜 ConnectionFactoryOptions 가 아니라 드라이버 전용 빌더를 쓰는가</b> —
     * {@code Option.valueOf("문자열")} 방식은 옵션 이름이 틀려도 컴파일 되고 예외도 없이
     * <b>조용히 무시</b>된다. 실제로 이전에 {@code preferStream} 이라는 존재하지 않는 이름을 쓰고 있었고,
     * 그래서 커서 설정이 전혀 적용되지 않았다. 빌더는 모든 값이 컴파일 시점에 검증되므로
     * 같은 사고가 구조적으로 재발하지 않는다.</p>
     */
    @Bean
    public ConnectionFactory mssqlConnectionFactory() {

        MssqlConnectionConfiguration configuration = MssqlConnectionConfiguration.builder()
                .host(mssqlHost)
                .port(mssqlPort)
                .username(mssqlUser)
                .password(mssqlPassword)
                .database(mssqlDatabase)
                // 커서(sp_cursorfetch) 사용 여부 — 설정값(storage.mssql.prefer-cursored-execution, 기본 false).
                //   커서를 쓰면 서버가 결과를 128행씩 나눠 주고 그때마다 왕복이 발생한다.
                //   30만 행이면 왕복만 2천 회가 넘어 네트워크 지연이 그대로 사이클 시간이 된다.
                //   false 면 한 번의 실행으로 결과를 스트리밍해 왕복이 1회로 줄어든다.
                //   (쿼리별로 다르게 하려면 Predicate<String> 오버로드 사용 가능)
                .preferCursoredExecution(mssqlPreferCursoredExecution)
                .build();

        return new MssqlConnectionFactory(configuration);
    }

    // Postgres 커넥션 풀(2026-10-16) — 풀 없이 쿼리마다 연결을 열던 구조라 동시 쿼리가 몰리면 DB max_connections 를 넘겼다.
    // 상한을 두고 모자라면 에러 대신 대기한다. (MSSQL 쪽은 이번 범위 아님 — 그대로 둔다)
    @Value("${storage.postgres.pool.max-size:10}") int poolMaxSize;
    @Value("${storage.postgres.pool.initial-size:2}") int poolInitialSize;
    @Value("${storage.postgres.pool.max-idle-time:10m}") Duration poolMaxIdleTime;
    @Value("${storage.postgres.pool.max-life-time:30m}") Duration poolMaxLifeTime;
    @Value("${storage.postgres.pool.max-acquire-time:10s}") Duration poolMaxAcquireTime;

    /** 풀로 감싼 Postgres 연결. 종료 시 dispose 로 연결을 정리한다. */
    @Primary
    @Bean(destroyMethod = "dispose")
    public ConnectionPool postgresConnectionFactory() {
        ConnectionFactory raw = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, pgDriver)
                .option(HOST, pgHost)
                .option(PORT, pgPort)
                .option(USER, pgUser)
                .option(PASSWORD, pgPassword)
                .option(DATABASE, pgDatabase)
                .option(Option.valueOf("schema"), pgSchema)
                .build());

        return new ConnectionPool(ConnectionPoolConfiguration.builder(raw)
                .name("hana-gateway-pg")
                .initialSize(poolInitialSize)
                .maxSize(poolMaxSize)
                .maxIdleTime(poolMaxIdleTime)        // 오래 논 연결은 정리(방화벽·DB 쪽 끊김 대비)
                .maxLifeTime(poolMaxLifeTime)        // 오래된 연결은 교체
                .maxAcquireTime(poolMaxAcquireTime)  // 연결을 이만큼 못 얻으면 에러(무한 대기 방지)
                .build());
    }

    @Bean
    public DatabaseClient mssqlDatabaseClient(@Qualifier("mssqlConnectionFactory") ConnectionFactory factory) {
        return DatabaseClient.create(factory);
    }

    @Primary
    @Bean
    public DatabaseClient postgresDatabaseClient(@Qualifier("postgresConnectionFactory") ConnectionFactory factory) {
        return DatabaseClient.create(factory);
    }

    /* ───────────── 주차 시스템 MSSQL(DRPKDBV5) — 입차·출차 프로시저 호출 전용 (2026-10-13) ─────────────
     * 수집용 MSSQL(SI 뷰)과 서버·DB 가 다르고 용도도 다르다(5분에 한 번 프로시저 2개). 그래서 연결을 따로 둔다.
     * 커넥션은 호출할 때만 열고 닫는다(풀 없음) — 5분 주기라 상시 연결을 들고 있을 이유가 없다.
     * 접속 정보는 환경 변수(PARKING_MSSQL_*)로 받는다. 비어 있어도 빈 생성은 되고, 실제 호출 때 실패 로그만 남는다.
     */
    @Value("${storage.parking-mssql.host}") String parkingHost;
    @Value("${storage.parking-mssql.port}") int parkingPort;
    @Value("${storage.parking-mssql.user}") String parkingUser;
    @Value("${storage.parking-mssql.password}") String parkingPassword;
    @Value("${storage.parking-mssql.database}") String parkingDatabase;

    @Bean
    public ConnectionFactory parkingMssqlConnectionFactory() {
        MssqlConnectionConfiguration configuration = MssqlConnectionConfiguration.builder()
                .host(parkingHost)
                .port(parkingPort)
                .username(parkingUser)
                .password(parkingPassword)
                .database(parkingDatabase)
                // 결과가 수백 행이라 커서로 나눠 받을 이유가 없다 — 한 번에 스트리밍
                .preferCursoredExecution(false)
                .build();
        return new MssqlConnectionFactory(configuration);
    }

    @Bean
    public DatabaseClient parkingMssqlDatabaseClient(@Qualifier("parkingMssqlConnectionFactory") ConnectionFactory factory) {
        return DatabaseClient.create(factory);
    }
}
