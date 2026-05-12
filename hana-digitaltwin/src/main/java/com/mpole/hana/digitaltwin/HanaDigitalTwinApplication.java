package com.mpole.hana.digitaltwin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.mpole.hana")
public class HanaDigitalTwinApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanaDigitalTwinApplication.class, args);
    }
}
