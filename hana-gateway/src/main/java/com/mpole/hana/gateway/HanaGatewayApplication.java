package com.mpole.hana.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.mpole.hana")
public class HanaGatewayApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        // Netty의 최신 메모리 관리 알고리즘(AdaptivePoolingAllocator)과 MSSQL 드라이버가 충돌
        // 로그 상의 io.netty.buffer.AdaptivePoolingAllocator는 Netty 4.1.100 버전 즈음부터 도입된 최신 적응형 메모리 할당기입니다.
        //1~2회차 성공: 데이터 양을 보고 Netty가 "아, 이 정도 메모리가 필요하구나"라고 학습하며 버퍼를 할당합니다.

        //3회차 실패: Netty가 이전 성공 사례를 바탕으로 메모리 버퍼를 재조정(Resize/Recycle)하려고 시도합니다.
        // 이때 MSSQL 드라이버가 버퍼를 "이미 다 썼다"고 판단해 해제(Release)했는데,
        // Netty 엔진은 "아직 쓰고 있다"고 착각하거나 그 반대의 상황이 발생합니다.

        // Postgres는 왜 괜찮나? PG용 R2DBC 드라이버는 Netty의 이 특정 할당 방식을 건드리지 않거나,
        // 메모리 참조(Reference Count) 관리가 훨씬 보수적으로 설계되어 있어 충돌이 나지 않는 것입니다.
        System.setProperty("io.netty.allocator.type", "pooled");
        SpringApplication.run(HanaGatewayApplication.class, args);
    }
}
