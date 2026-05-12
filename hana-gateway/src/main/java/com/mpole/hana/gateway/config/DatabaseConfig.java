package com.mpole.hana.gateway.config;

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

    // Postgres 설정값 주입
    @Value("${storage.postgres.driver}") String pgDriver;
    @Value("${storage.postgres.host}") String pgHost;
    @Value("${storage.postgres.port}") int pgPort;
    @Value("${storage.postgres.user}") String pgUser;
    @Value("${storage.postgres.password}") String pgPassword;
    @Value("${storage.postgres.database}") String pgDatabase;
    @Value("${storage.postgres.schema}") String pgSchema;

    @Bean
    public ConnectionFactory mssqlConnectionFactory() {


        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, mssqlDriver)
                .option(HOST, mssqlHost)
                .option(PORT, mssqlPort)
                .option(USER, mssqlUser)
                .option(PASSWORD, mssqlPassword)
                .option(DATABASE, mssqlDatabase)
                // 메모리 에러 방지를 위한 스트리밍 옵션 추가 (추천)
                .option(Option.valueOf("preferStream"), true)
                .build());
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
