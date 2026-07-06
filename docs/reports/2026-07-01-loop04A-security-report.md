# AGENT 작업 보고서

## 1. 작업 AGENT
spring-backend-agent

## 2. 작업 일시
2026-07-01

## 3. 작업 목표
Loop 04-A: `/api/monitor/**` 엔드포인트를 admin JWT(ADMIN 역할)로 보호한다.
승인 요청서: `qa/change-requests/2026-07-01-loop04-monitor-admin-auth.md`.
범위 제한: `hana-digitaltwin/.../global/security/SecurityConfig.java` 한 파일만 수정.

## 4. 수정한 파일
- `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/global/security/SecurityConfig.java`

## 5. 생성한 파일
- `docs/reports/2026-07-01-loop04A-security-report.md` (본 보고서)

## 6. 주요 작업 내용
`authorizeHttpRequests` 체인의 `.authenticated()` 블록과 `.permitAll()` 블록 사이에
`/api/monitor/**` 를 ADMIN 역할로 강제하는 규칙 한 줄을 추가했다.

변경 전 (핵심 블록):
```java
        .requestMatchers(
                "/api/device-system/roles/**",
                "/api/auth/refresh",
                "/api/auth/change-password",
                "/api/auth/me",
                "/api/auth/sessions/**"
                ).authenticated()
        .requestMatchers(
                "/api/**",
                "/actuator/**",
                ... (permitAll)
        ).permitAll()
        .requestMatchers("/api/auth/unlock/**").hasRole("ADMIN")
        .anyRequest().authenticated()
```

변경 후 (핵심 블록):
```java
        .requestMatchers(
                "/api/device-system/roles/**",
                "/api/auth/refresh",
                "/api/auth/change-password",
                "/api/auth/me",
                "/api/auth/sessions/**"
                ).authenticated()
        // 모니터링 상태 API는 ADMIN 전용 (permitAll "/api/**" 보다 먼저 선언되어야 첫 매칭 적용됨)
        .requestMatchers("/api/monitor/**").hasRole("ADMIN")
        .requestMatchers(
                "/api/**",
                "/actuator/**",
                ... (permitAll)
        ).permitAll()
        .requestMatchers("/api/auth/unlock/**").hasRole("ADMIN")
        .anyRequest().authenticated()
```

diff 요약: `.authenticated()` 블록 종료 직후, `.requestMatchers("/api/**", ...)` permitAll
블록 시작 직전에 주석 1줄 + 규칙 1줄 (총 2줄) 추가. 다른 항목(actuator permitAll,
authenticated 목록, unlock ADMIN, anyRequest 등)은 변경하지 않음.

## 7. 기술적 판단 근거

### (1) 규칙 순서 / 첫 매칭 우선
Spring Security의 `authorizeHttpRequests` 는 `requestMatchers` 를 **선언 순서대로 평가하여
첫 매칭 규칙**을 적용한다. `/api/monitor/status` 는 `/api/monitor/**` 와 `/api/**` 두 패턴에
모두 매칭되므로, ADMIN 강제가 유효하려면 반드시 `/api/**` permitAll **앞**에 놓여야 한다.
추가한 위치가 permitAll 블록보다 앞이므로 `/api/monitor/**` 요청은 ADMIN 규칙에 먼저
매칭되어 permitAll 로 흘러가지 않는다. → 요구사항 충족.

### (2) hasRole("ADMIN") 의 ROLE_ 접두사 정합성 (핵심 확인 사항)
`SecurityConfig.java` line 31-34 에 다음 빈이 존재한다:
```java
@Bean
static GrantedAuthorityDefaults grantedAuthorityDefaults() {
    return new GrantedAuthorityDefaults("");
}
```
이는 Spring Security 의 기본 role 접두사(`ROLE_`)를 **빈 문자열로 재정의**한다. 따라서
`hasRole("ADMIN")` 은 이 프로젝트에서 `ROLE_ADMIN` 이 아니라 **접두사 없는 authority
`ADMIN`** 을 요구한다.

권한 매핑 체인을 코드에서 확인:
- `AuthService`(line 57, 103): `List<String> roles = user.roleNames();`
- `User.roleNames()`(User.java line 116-118): `userRole.getRole().getRoleName()` — DB 의
  `role_name` 값을 접두사 부착 없이 그대로 반환.
- `JwtTokenProvider.generateAccessToken(...).claim("roles", roles)` — role_name 그대로 JWT
  클레임에 저장.
- `JwtAuthenticationFilter.authenticate()`(line 65-68): `jwtTokenProvider.getRoles(token)`
  결과를 `SimpleGrantedAuthority::new` 로 그대로 매핑 (접두사 부착 없음).

결론: JWT authority = DB role_name 그대로, `hasRole("ADMIN")` = authority `ADMIN` 요구.
기존 `/api/auth/unlock/**` 의 `hasRole("ADMIN")` 과 **완전히 동일한 규약**을 신규 규칙이
그대로 따르므로 불일치 위험 없음. 단, 이 체계가 정상 동작하는 전제는 **DB role_name 이
정확히 `ADMIN`** 이어야 한다는 점이다(이는 기존 unlock 규칙에도 동일하게 적용되던 전제).

### (3) actuator permitAll 유지
지시대로 `/actuator/**` permitAll 항목은 변경하지 않았다. actuator 는 04-B 에서 localhost
로 분리 예정이며 self-poll 을 위해 현재 permitAll 유지가 필요하다.

## 8. 다른 AGENT 영역 영향 여부
- **04-B (devops-agent, yml actuator localhost 분리)**: 본 변경은 actuator 규칙을 건드리지
  않았으므로 04-B 와 독립적. 영향 없음.
- **04-C (aggregator 폴링 포트 갱신)**: `/api/monitor/**` 가 이제 ADMIN 토큰을 요구하므로,
  aggregator/폴러가 `/api/monitor/status` 를 호출한다면 **ADMIN JWT 를 헤더에 실어야 함**.
  포함 여부는 04-C 담당이 확인 필요. 영향 있음(요청 필요 가능).
- **프론트 (react-frontend-agent)**: `/api/monitor/**` 호출 시 **ADMIN 역할 토큰**이 없으면
  403(권한 부족) 또는 401(미인증). 모니터 상태 화면은 ADMIN 사용자로 제한되어야 함. 영향
  있음(프론트 API 계약 안내 필요).
- hasRole ADMIN 의 ROLE_ 접두사 정합성: 위 7-(2) 참조. `GrantedAuthorityDefaults("")` 로
  접두사가 제거되어 있고, 기존 권한 매핑이 접두사 없는 `ADMIN` authority 를 사용하므로
  신규 규칙과 정합함(기존 unlock 규칙과 동일 규약).

## 9. 남은 이슈
- 본 변경으로 인해 인증되지 않은/비-ADMIN 요청의 `/api/monitor/**` 응답은 401/403 으로
  바뀐다. 04-C 폴러가 인증 없이 호출 중이었다면 폴링이 실패할 수 있으므로 04-C 에서 토큰
  주입이 반드시 병행되어야 한다(교차 의존성).
- DB role_name 의 실제 값이 `ADMIN` 인지 여부는 database-agent 영역이라 본 Loop 에서
  DB 를 조회/수정하지 않았다. 기존 unlock 규칙이 동작 중이라면 전제 충족으로 간주.

## 10. QA 요청 사항
- SecurityConfig.java 에서 `/api/monitor/**` ADMIN 규칙이 `/api/**` permitAll **앞**에
  선언되어 첫 매칭 우선을 만족하는지 확인.
- `/actuator/**` permitAll, authenticated 목록, `/api/auth/unlock/**` ADMIN, anyRequest 등
  기타 항목이 변경되지 않았는지 확인.
- `GrantedAuthorityDefaults("")` 와 JWT authority 매핑을 근거로 `hasRole("ADMIN")` 정합성
  판단이 타당한지 검토.
- 변경 범위가 SecurityConfig.java 한 파일로 제한되었는지 확인.
- 컴파일: `./gradlew :hana-digitaltwin:compileJava -q` EXIT=0 (성공) 확인 완료.

## 11. 다음 Loop 제안
- 04-B: `application.yml` actuator management.server.port 또는 endpoints 를 localhost 로
  분리 (devops-agent, 승인 게이트).
- 04-C: aggregator/모니터 폴러에 ADMIN JWT 주입 및 폴링 포트 갱신.
- 프론트: 모니터 상태 화면을 ADMIN 역할 가드로 보호하고 API 클라이언트에 토큰 첨부.
