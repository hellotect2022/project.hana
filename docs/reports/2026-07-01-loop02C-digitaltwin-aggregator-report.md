# Loop 02-C 작업 보고서 — DigitalTwin 모니터링 Aggregator API

- 작업일: 2026-07-01
- 담당 AGENT: spring-backend-agent
- 대상 모듈: hana-digitaltwin
- 승인 요청서: `qa/change-requests/2026-06-30-loop02-monitoring-actuator-aggregator.md` (승인 완료)
- 선행: Loop A(hana-common MonitorSnapshot DTO) QA PASS, Loop B(actuator 설정) QA PASS

---

## 1. 작업 목표

event / gateway / digitaltwin(self) 3개 서비스의 Spring Boot Actuator를 HTTP 폴링해
hana-common의 `MonitorSnapshot` DTO로 정규화, 단일 aggregator API로 통합 조회한다.

## 2. 변경 요약

- 신규 파일 **2개만** 생성. 기존 파일 수정 0건.
- 패키지: `com.mpole.hana.digitaltwin.monitor`
- 엔드포인트: **`GET /api/monitor/status`** → `ApiResponse<MonitorSnapshot>`
- RestClient(spring-web 내장, 추가 의존성 없음)로 3개 대상 순차 폴링.

## 3. 변경 파일 목록 (신규)

| 파일 | 역할 |
|---|---|
| `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/monitor/service/MonitorService.java` | Actuator 폴링·정규화 서비스 |
| `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/monitor/api/MonitorController.java` | `GET /api/monitor/status` aggregator 컨트롤러 |

## 4. 폴링 로직 요약

### 폴링 대상 (yml 무수정, @Value 기본값 내장)
| 서비스 | 프로퍼티 키 | 기본값 |
|---|---|---|
| event | `hdt.monitor.targets.event` | `http://localhost:8081` |
| gateway | `hdt.monitor.targets.gateway` | `http://localhost:8090` (전용 management 포트) |
| digitaltwin(self) | `hdt.monitor.targets.digitaltwin` | `http://localhost:8084` |

### 절차 (대상별)
1. `GET {base}/actuator/health` 왕복 → `responseTimeMs` 측정, `status` 필드를 LiveStatus로 매핑.
2. 예외/타임아웃/비2xx → `status=DOWN`, `resource=null`, `responseTimeMs=null`, `detail`에 사유 요약.
3. health가 UP이 아니면(DOWN/UNKNOWN) 리소스 미수집(`resource=null`).
4. UP이면 리소스 수집: `GET {base}/actuator/metrics/{name}` 호출 후 `measurements`에서 `statistic=VALUE`의 `value` 추출.
5. 3개 결과로 `List<ServiceMonitor>` 구성 → `MonitorSnapshot(timestamp=OffsetDateTime.now(), services)` 반환.

### RestClient timeout
- `SimpleClientHttpRequestFactory`(spring-web 내장)로 connect/read timeout 각 **2000ms** 설정.
- 한 서비스가 느려도 전체 응답이 지연되지 않도록 방어.

### metric 매핑
| metric | 대상 필드 | 타입/환산 |
|---|---|---|
| `jvm.memory.used` | `heapUsedBytes` | long |
| `jvm.memory.max` | `heapMaxBytes` | long (없거나 -1이면 그대로 보관) |
| `process.cpu.usage` | `processCpuLoad` | double 0~1, 실패 시 -1.0 |
| `system.cpu.usage` | `systemCpuLoad` | double, 실패 시 -1.0 |
| `jvm.threads.live` | `threadCount` | int |
| `process.uptime` | `uptimeMs` | 초 gauge × 1000 → ms |

- metric 응답의 `measurements`가 널/빈 리스트/파싱불가면 방어하여 fallback 반환.
- **개별 metric 수집 실패는 해당 필드만 센티넬로 두고 서비스 자체는 UP 유지.**

## 5. LiveStatus 매핑 규칙 (명문화)

health 응답 `status` 문자열(대소문자 무시) 기준:

| health status | LiveStatus |
|---|---|
| `UP` | `UP` |
| `DOWN`, `OUT_OF_SERVICE` | `DOWN` |
| 그 외 값 / null / 파싱불가 | `UNKNOWN` |
| health 요청 자체 실패(예외/타임아웃/비2xx) | `DOWN` (detail에 사유) |

## 6. -1.0 센티넬 의미 (Loop A QA 후속항목 명문화)

`ResourceUsage`의 double 필드(`processCpuLoad`, `systemCpuLoad`)는 정상 범위가 `0.0~1.0`이다.
해당 metric을 수집하지 못한 경우(대상에 metric 미노출, 파싱 실패, 개별 metric 요청 실패)
정상값과 구분하기 위해 **`-1.0`을 "측정 불가" 센티넬**로 사용한다.
소비 측(프론트/대시보드)은 음수 값을 "N/A"로 해석해야 한다.
long/int 필드(`heapMaxBytes`, `threadCount` 등)는 수집 실패 시 `0`을 사용한다.
(`jvm.memory.max`가 -1로 노출되는 환경에서는 원값 -1을 그대로 보관.)

## 7. 다른 AGENT 영역 영향

- **SecurityConfig 무수정**: `/api/monitor/status`는 별도 permitAll을 추가하지 않아 기존 JWT
  인증 필터를 그대로 통과해야 하는 인증 필요 엔드포인트로 둔다. (운영/관제용 민감 API)
  - 참고: 기존 SecurityConfig의 permitAll 목록에 광범위한 `/api/**` 패턴이 존재한다. 현
    체인 순서상 이 패턴이 `/api/monitor/**`에도 매칭될 수 있다. 본 Loop 범위(신규 파일만
    생성, SecurityConfig 무수정)를 지키기 위해 컨트롤러 코드에서는 이를 건드리지 않았다.
    실제 인증 강제가 필요하면 SecurityConfig 조정을 devops/보안 담당과 별도 승인 절차로
    진행해야 한다. **QA 확인 요청 항목**으로 아래에 기재.
- **application.yml 무수정**: 폴링 대상 URL은 `@Value` 기본값을 내장하여 yml 없이 동작.
  운영 환경에서 URL 변경이 필요하면 `hdt.monitor.targets.*` 키를 yml에 추가하면 되며 본
  Loop에서는 추가하지 않았다.
- **build.gradle 무수정**: RestClient는 spring-web 내장, 신규 의존성 없음.
- hana-common / hana-event / hana-gateway: 무수정. hana-common DTO는 소비만.

## 8. 컴파일 검증

```
./gradlew :hana-digitaltwin:compileJava -q
```
→ 에러/경고 출력 없이 성공. (IDE 진단상 `@SuppressWarnings("unchecked")` 불필요 경고 1건은 제거 완료)

## 9. QA 요청 항목

1. 신규 2개 파일이 기존 컨트롤러/서비스 컨벤션(패키지 구조, `ApiResponse` 래퍼, Swagger
   `@Operation`/`@Tag`)과 일치하는지.
2. LiveStatus 매핑 규칙 / -1.0 센티넬 처리 정확성.
3. metric `measurements` 파싱의 널/빈 리스트 방어 적절성.
4. **보안 확인(중요)**: 기존 SecurityConfig의 `/api/**` permitAll 패턴으로 인해
   `/api/monitor/status`가 인증 없이 열릴 가능성. 본 Loop 범위상 SecurityConfig를
   수정하지 않았으므로, 인증 강제가 필요한지 여부와 후속 조치(SecurityConfig 변경 승인)를
   판단 요청.
5. RestClient 2초 timeout이 관제 폴링 주기에 적절한지.
