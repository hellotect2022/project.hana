package com.mpole.hana.gateway.config;

import io.r2dbc.mssql.MssqlConnectionConfiguration;
import io.r2dbc.mssql.MssqlConnectionFactory;
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

    @Primary
    @Bean
    public ConnectionFactory postgresConnectionFactory() {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, pgDriver)
                .option(HOST, pgHost)
                .option(PORT, pgPort)
                .option(USER, pgUser)
                .option(PASSWORD, pgPassword)
                .option(DATABASE, pgDatabase)
                .option(Option.valueOf("schema"), pgSchema)
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
}
