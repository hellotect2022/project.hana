package com.mpole.hana.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.mpole.hana")
@ConfigurationPropertiesScan
public class HanaEventApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanaEventApplication.class, args);
    }
}
