package com.mpole.hana.event.config;

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
// Postgres 설정값 주입
    @Value("${storage.postgres.driver}") String pgDriver;
    @Value("${storage.postgres.host}") String pgHost;
    @Value("${storage.postgres.port}") int pgPort;
    @Value("${storage.postgres.user}") String pgUser;
    @Value("${storage.postgres.password}") String pgPassword;
    @Value("${storage.postgres.database}") String pgDatabase;
    @Value("${storage.postgres.schema}") String pgSchema;

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

    @Primary
    @Bean
    public DatabaseClient postgresDatabaseClient(@Qualifier("postgresConnectionFactory") ConnectionFactory factory) {
        return DatabaseClient.create(factory);
    }
}
