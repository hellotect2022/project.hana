package com.mpole.hana.digitaltwin.global.security;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    static GrantedAuthorityDefaults grantedAuthorityDefaults() {
        return new GrantedAuthorityDefaults("");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // ── HQS 연동 ──────────────────────────────────────────────
                        // 아래 "/api/**" permitAll 보다 반드시 위에 있어야 한다.
                        // Spring Security 는 먼저 매칭된 규칙이 이기므로, 순서를 바꾸면
                        // 이 규칙들이 통째로 무시되고 HQS API 가 다시 무인증으로 열린다.
                        .requestMatchers("/api/v1/hqs/login/external").permitAll()   // 토큰 발급 — 인증 전 호출
                        .requestMatchers("/api/v1/hqs/**").hasAnyRole("ADMIN", "HQS") // HQS 용
                        // 모의 이벤트(SOP 시나리오 훈련, 2026-10-07) — 켤 때는 아래 주석을 풀고 "/api/**" permitAll 보다 위에 둔다.
                        // 지금은 다른 /api/** 와 같이 무인증 통과(사용자 결정: 일단 권한 없이 전부 통과, 역할은 ADMIN·UNITY 예정).
                        // .requestMatchers("/api/event/simulate/**").hasAnyRole("ADMIN", "UNITY")
                        // ──────────────────────────────────────────────────────────
                        .requestMatchers(
                                "/api/device-system/roles/**",
                                "/api/auth/change-password",
                                "/api/auth/me",
                                "/api/auth/sessions/**"
                                ).authenticated()
                        .requestMatchers(
                                "/api/**",
                                "/internal/**",   // 서버 간 내부 API(event 서버 → 이벤트 연동 디바이스 제어, 2026-10-12). 외부 비노출 전제
                                "/actuator/**",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/health",
                                "/api/test",
                                "/stomp/**",
                                "/ws/**",
                                // Swagger UI / OpenAPI 문서 (인증 없이 접근 — Authorize 버튼으로 토큰 입력)
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/docs/openapi3.json"
                        ).permitAll()
                        .requestMatchers("/api/auth/unlock/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }


    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
