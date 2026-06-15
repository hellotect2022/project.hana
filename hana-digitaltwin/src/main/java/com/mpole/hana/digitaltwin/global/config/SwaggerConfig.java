package com.mpole.hana.digitaltwin.global.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.filters.OpenApiMethodFilter;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;

@Configuration
public class SwaggerConfig {
//    @Bean
//    public OpenApiMethodFilter apiMethodFilter() {
//        return method -> method.isAnnotationPresent(Operation.class);
//    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                // 💡 여기 Components 설정을 추가해 줍니다!
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", // 👈 @SecurityRequirement(name = "bearerAuth") 와 이름이 일치해야 합니다!
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(SecurityScheme.In.HEADER)
                                        .name("Authorization")));
    }

    @Bean
    public OpenApiMethodFilter apiMethodFilter() {
        return method -> {
            // 1. 실제 컨트롤러 메서드에 @Operation이 붙어있는지 확인
            if (method.isAnnotationPresent(Operation.class)) {
                return true;
            }

            // 2. 오버라이딩된 메서드인 경우, 부모 클래스나 인터페이스의 메서드까지 뒤져서 확인
            Class<?> declaringClass = method.getDeclaringClass();
            for (Class<?> iface : declaringClass.getInterfaces()) {
                try {
                    // 동일한 이름과 파라미터를 가진 인터페이스의 메서드를 찾음
                    Method interfaceMethod = iface.getMethod(method.getName(), method.getParameterTypes());
                    // 💡 인터페이스 메서드에 @Operation이 붙어있다면 패스!
                    if (interfaceMethod.isAnnotationPresent(Operation.class)) {
                        return true;
                    }
                } catch (NoSuchMethodException e) {
                    // 인터페이스에 선언되지 않은 메서드(refresh 등)는 무시하고 다음 인터페이스 검사
                }
            }

            return false; // 그 어디에도 @Operation이 없으면 스웨거 문서에서 제외
        };
    }
}
