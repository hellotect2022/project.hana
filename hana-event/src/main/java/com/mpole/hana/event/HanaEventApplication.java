package com.mpole.hana.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.mpole.hana")
public class HanaEventApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanaEventApplication.class, args);
    }
}
