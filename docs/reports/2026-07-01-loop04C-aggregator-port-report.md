# Loop 04-C 작업 보고서 — MonitorService aggregator 폴링 대상 포트 갱신

- 작성일: 2026-07-01
- 담당 AGENT: spring-backend-agent
- 승인 요청서: `qa/change-requests/2026-07-01-loop04-monitor-admin-auth.md`
- 관련 Loop: 04-A(모니터 상태 API ADMIN 보호), 04-B(actuator 전용 localhost 포트 분리)

## 1. 작업 목표

Loop 04-B에서 각 서비스의 actuator가 전용 localhost 포트(event 8091, gateway 8090,
digitaltwin 8085, 모두 127.0.0.1)로 분리됨에 따라, digitaltwin의 aggregator
(`MonitorService`)가 폴링하는 기본 대상 포트를 새 포트로 갱신한다.

## 2. 변경 범위 (엄격)

- 수정 파일: **1개**
  - `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/monitor/service/MonitorService.java`
- yml / SecurityConfig / build.gradle / DTO 등 그 외 파일은 일절 수정하지 않음.

## 3. 변경 내용 — @Value 기본값 diff

생성자 인자의 `@Value` 콜론 뒤 default 값만 변경. 프로퍼티 키 이름
(`hdt.monitor.targets.*`)과 나머지 로직은 그대로 유지하여 yml override 여지 보존.

| 대상 | 프로퍼티 키 | 변경 전 기본값 | 변경 후 기본값 |
|---|---|---|---|
| event | `hdt.monitor.targets.event` | `http://localhost:8081` | `http://localhost:8091` |
| gateway | `hdt.monitor.targets.gateway` | `http://localhost:8090` | `http://localhost:8090` (변경 없음) |
| digitaltwin(self) | `hdt.monitor.targets.digitaltwin` | `http://localhost:8084` | `http://localhost:8085` |

```diff
-            @Value("${hdt.monitor.targets.event:http://localhost:8081}") String eventBaseUrl,
+            @Value("${hdt.monitor.targets.event:http://localhost:8091}") String eventBaseUrl,
             @Value("${hdt.monitor.targets.gateway:http://localhost:8090}") String gatewayBaseUrl,
-            @Value("${hdt.monitor.targets.digitaltwin:http://localhost:8084}") String digitaltwinBaseUrl,
+            @Value("${hdt.monitor.targets.digitaltwin:http://localhost:8085}") String digitaltwinBaseUrl,
```

- `hdt.monitor.network-targets` 기본값(192.168.250.181:5432,192.168.250.249:9092)은 범위 밖이므로 변경하지 않음.

## 4. 기술 판단 근거

- aggregator(`MonitorService`)는 각 서비스의 `/actuator/{health,metrics,info}`만 폴링한다.
  이 경로는 permitAll + localhost 바인딩이므로 **인증(JWT) 불필요**하다. 따라서 폴러에
  토큰을 주입하지 않음 — 순수 포트 숫자만 갱신했다.
- Loop 04-A에서 ADMIN으로 보호된 것은 프론트가 호출하는 `/api/monitor/status`
  (digitaltwin 자기 컨트롤러)이다. aggregator는 이 엔드포인트를 호출하지 않고 자기 자신이
  그 응답을 생성한다. self 대상(`digitaltwinBaseUrl`)도 `localhost:8085/actuator/*`를
  호출하는 것이지 `/api/monitor`를 호출하지 않는다. 그러므로 self 폴링에도 토큰이 불필요하다.
- 키 이름을 유지했으므로 운영 환경에서 yml(`hdt.monitor.targets.*`) override 시 기존 방식
  그대로 동작한다. 기본값 변경은 yml 미설정 시의 fallback만 바꾼다.

## 5. 검증

- 컴파일: `./gradlew :hana-digitaltwin:compileJava -q` → **성공(EXIT=0)**.
- 로직 무변경: 생성자 필드 대입/폴링 흐름/파싱 헬퍼 등 나머지 코드 미변경.

## 6. 다른 AGENT 영향

- **없음.** 순수 포트 기본값 변경으로 API 계약/DTO/스키마/빌드/yml에 영향 없음.
- 폴러는 무인증 actuator(localhost)만 호출하므로 **토큰 미주입이 정상**이다.

## 7. QA 요청 항목

- 본 변경은 Loop 04-A / 04-B / 04-C **통합 QA 대상**임.
- @Value 기본값 3건 중 event(8091)/digitaltwin(8085) 변경, gateway(8090) 유지 확인.
- 폴러에 JWT 토큰이 주입되지 않았고, actuator localhost permitAll 전제와 일치하는지 확인.
- 키 이름(`hdt.monitor.targets.*`) 유지로 yml override 여지 보존 여부 확인.
