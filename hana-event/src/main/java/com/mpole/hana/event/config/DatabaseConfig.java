package com.mpole.hana.event.config;

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
// Postgres 설정값 주입
    @Value("${storage.postgres.driver}") String pgDriver;
    @Value("${storage.postgres.host}") String pgHost;
    @Value("${storage.postgres.port}") int pgPort;
    @Value("${storage.postgres.user}") String pgUser;
    @Value("${storage.postgres.password}") String pgPassword;
    @Value("${storage.postgres.database}") String pgDatabase;
    @Value("${storage.postgres.schema}") String pgSchema;

    // 커넥션 풀(2026-10-16) — 풀 없이 쿼리마다 연결을 열던 구조라 동시 쿼리가 몰리면 DB max_connections 를 넘겼다
    // ("최대 동시 접속 수를 초과했습니다"). 상한을 두고 모자라면 에러 대신 대기한다.
    @Value("${storage.postgres.pool.max-size:20}") int poolMaxSize;
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
                .name("hana-event-pg")
                .initialSize(poolInitialSize)
                .maxSize(poolMaxSize)
                .maxIdleTime(poolMaxIdleTime)        // 오래 논 연결은 정리(방화벽·DB 쪽 끊김 대비)
                .maxLifeTime(poolMaxLifeTime)        // 오래된 연결은 교체
                .maxAcquireTime(poolMaxAcquireTime)  // 연결을 이만큼 못 얻으면 에러(무한 대기 방지)
                .build());
    }

    @Primary
    @Bean
    public DatabaseClient postgresDatabaseClient(@Qualifier("postgresConnectionFactory") ConnectionFactory factory) {
        return DatabaseClient.create(factory);
    }
}
