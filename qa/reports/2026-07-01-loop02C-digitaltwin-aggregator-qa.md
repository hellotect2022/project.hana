# QA 검증 보고서 — Loop 02-C DigitalTwin 모니터링 Aggregator API

- 검증일: 2026-07-01
- 검증자: qa-code-reviewer
- 대상 모듈: hana-digitaltwin
- 검증 대상(신규 2개):
  - `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/monitor/service/MonitorService.java`
  - `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/monitor/api/MonitorController.java`
- 엔드포인트: `GET /api/monitor/status` → `ApiResponse<MonitorSnapshot>`
- 근거 문서: 승인요청서 `qa/change-requests/2026-06-30-loop02-monitoring-actuator-aggregator.md`,
  작업보고서 `docs/reports/2026-07-01-loop02C-digitaltwin-aggregator-report.md`
- 선행: Loop A(hana-common DTO), Loop B(actuator 설정) — 본 검증에서 DTO/포트/노출설정 정합성 재확인 완료.

---

## 전체 판정: **PASS (조건부 — WARNING 후속항목 2건 동반)**

신규 코드 자체의 기능/구조/컨벤션은 결함 없이 PASS. 다만 본 Loop 범위 밖 사안 2건
(무인증 노출, 순차 폴링 최악 지연)을 WARNING 위험도로 기록한다. 이 2건은 신규 코드
결함이 아니라 설계/정책 판단 사안이므로 FAIL 처리하지 않는다.

---

## 항목별 검증 결과

### 1. 기존 컨트롤러/서비스 컨벤션 준수 — PASS
- `@RestController` + `@RequestMapping("/api/monitor")` + `@RequiredArgsConstructor`
  구조가 기존 `UserController`(L17-20)와 동일 패턴.
  (MonitorController L22-26)
- 응답을 `ResponseEntity<ApiResponse<T>>`로 감싸고 `ApiResponse.success(msg, data)`
  정적 팩토리를 사용 — 기존 컨벤션 일치. (MonitorController L40-42, ApiResponse L15-21)
- Swagger `@Tag`(L25) + `@Operation`(L31-37) 명세 존재. `@SecurityRequirement(name="bearerAuth")`
  까지 부여해 문서상 인증 필요 API로 표기(문서 레벨). 컨벤션 이상.
- 패키지 분리 `monitor/api`, `monitor/service` — 기존 도메인 레이아웃과 일치.
- 근거: `hana-digitaltwin/.../iam/user/api/UserController.java` L1-45 대비.

### 2. hana-common DTO 사용 정확성 — PASS
- `MonitorSnapshot.builder().timestamp(...).services(...)` — record 필드
  (`timestamp`, `services`) 정확 매핑. (MonitorService L65-68, MonitorSnapshot L13-16)
- `ServiceMonitor.builder()` 6개 필드(`service/status/responseTimeMs/resource/detail`)
  모두 존재하는 필드에 대응. (MonitorService L86-116, ServiceMonitor L10-16)
- `ResourceUsage.builder()` 6개 필드 타입 일치: `heapUsedBytes(long)`, `heapMaxBytes(long)`,
  `processCpuLoad(double)`, `systemCpuLoad(double)`, `threadCount(int)`, `uptimeMs(long)`.
  (MonitorService L157-164, ResourceUsage L10-17)
- `responseTimeMs`는 `Long`(nullable) 필드에 실패 시 `null` 전달 — 타입/의미 정합. (L89)

### 3. LiveStatus 매핑 및 센티넬 처리 — PASS (주의 1건)
- 매핑 규칙(MonitorService L123-133): `UP→UP`, `DOWN/OUT_OF_SERVICE→DOWN`, 그 외/null→UNKNOWN.
  요청 자체 실패(예외/타임아웃/비2xx)→`DOWN`(L88) + detail 사유. 요구 규칙과 일치.
- 대소문자 무시(`raw.toUpperCase()`) 처리 적절.
- **주의(결함 아님)**: `LiveStatus` enum은 `UP/DOWN/UNKNOWN` 3개만 존재
  (LiveStatus.java L7-9). `OUT_OF_SERVICE`는 enum 상수가 아니라 health **문자열** 값을
  DOWN으로 흡수하는 처리로, 코드상 올바르다. 문서(작업보고서 §5)의
  "OUT_OF_SERVICE→DOWN"은 health status 문자열 기준 표기로 정합.
- 센티넬: double 필드 실패 시 `-1.0`(SENTINEL_DOUBLE, L30/150-151), long/int 실패 시 `0`
  (L148-149/152) 전달 — 작업보고서 §6 명세와 일치.
- health UP이 아니면 리소스 미수집(`resource=null`) — L98-107 정확.

### 4. metrics measurements 파싱 널/빈 방어 — PASS
- `body==null` 방어(L177), `measurements`가 List가 아니거나 비었을 때 방어
  (`!(... instanceof List<?>) || isEmpty()`, L181-183) → fallback 반환.
- 각 measurement가 Map인지 `instanceof` 확인(L186, L195), `value`가 Number인지 확인 후
  `doubleValue()`(L189, L197) — ClassCastException 방어 적절.
- `statistic=="VALUE"` 우선, 없으면 첫 항목 fallback(L184-200) — 요구사항 충족.
- 전체 try/catch로 예외를 fallback으로 흡수(L202-205) — 개별 metric 실패가 서비스
  UP 판정에 영향 없음(요구 격리 충족).

### 5. RestClient timeout 및 지연 격리 — PASS(코드) / WARNING(구조)
- `SimpleClientHttpRequestFactory`에 connect/read 각 `2000ms` 설정(L48-50) — 적용 확인.
  spring-web 내장, 추가 의존성 없음(승인요청서 §6-4 준수).
- **격리 구조 평가**: `getSnapshot()`은 3개 대상을 **순차** 폴링(L60-64). 대상별로
  health 1회 + (UP일 때) metric 6회 HTTP 왕복이 발생한다. 최악의 경우(대상 무응답으로
  connect+read 모두 2초 소진 시) 대상당 최대 지연이 누적되어 전체 응답이
  **대상수 × (health+metric 호출수) × timeout** 만큼 늘어날 수 있다.
  - health 단계에서 예외 발생 시 해당 대상은 metric 폴링을 건너뛰므로(L83-93),
    "완전 무응답" 대상은 대략 health 왕복(최대 ~4초) 수준에서 종료된다.
  - 그러나 health는 UP이지만 metrics 엔드포인트만 느린 병리적 케이스에서는 한 대상에서
    metric 6종 × 최대 4초까지 누적 가능(순차). 3대상 모두 이 상태면 관제 응답이
    수십 초까지 지연될 수 있다.
  - **결론**: 단일 서비스 지연이 다른 서비스 폴링을 "오염"시키지는 않으나(각 호출 timeout
    독립), 순차 구조상 지연이 **합산**된다. 관제 폴링 주기 대비 허용 가능한지 정책 판단
    필요 → WARNING 후속항목으로 기록. 병렬화(예: CompletableFuture)는 별도 개선 Loop 사안.

### 6. 단위 환산 정확성 — PASS
- `process.uptime`(초 gauge) → `uptimeMs`: 음수 방어 후 `×1000`(L154-155) 정확.
- `jvm.memory.used`/`jvm.memory.max` → long 캐스팅(L148-149) 정확.
- `jvm.threads.live` → int 캐스팅(L152), CPU load는 double 그대로(L150-151) 정확.
- **주의(WARNING 아님, 세부 정확성 참고)**: `/actuator/metrics/jvm.memory.used`를 태그
  없이 호출하면 actuator는 heap+nonheap 등 전 area의 VALUE **합산치**를 반환한다.
  따라서 `heapUsedBytes`/`heapMaxBytes`는 엄밀히는 "힙만"이 아니라 JVM 메모리 총합에
  가깝다. 또한 `jvm.memory.max`는 일부 pool이 -1(무제한)을 보고하는 환경에서 합산 결과가
  왜곡될 수 있다(작업보고서 §6 "-1 원값 보관" 언급과 부분 상충 가능). 크래시/기능 결함은
  아니며 소비 측 해석 주의 사항 → 세부 개선 후보로만 기록(판정 영향 없음).

### 7. 기존 파일 무수정 확인 — PASS
- `git status --porcelain`상 monitor 관련은 신규 미추적(`??`)만 존재:
  - `?? hana-digitaltwin/.../monitor/` (신규 2파일)
  - `?? hana-common/.../monitor/` (Loop A 신규, 본 Loop 대상 아님)
- 본 Loop 신규 대상 디렉터리에 수정(`M`) 표시 없음 → 기존 파일 무수정 확인.

### 8. 컴파일 검증 — PASS
- `./gradlew :hana-digitaltwin:compileJava -q` → `EXIT=0`, 에러/경고 출력 없음.

### 부가 정합성 재확인 — PASS
- 폴링 대상 기본 URL이 실제 포트와 일치:
  event `8081`(yml L2), gateway management `8090`(yml L77), digitaltwin `8084`(yml L2).
  MonitorService `@Value` 기본값(L39-41)과 전부 일치.
- 3개 대상 management 노출에 `metrics` 포함 확인(gateway yml L78-81: `health,info,metrics`).

---

## 보안 항목 (별도 기록 — 판정 참고, FAIL 사유 아님)

- **무인증 노출 확인**: `SecurityConfig.java` L52-67의 permitAll 목록에 `"/api/**"`(L53)가
  포함되어 있고, `/api/monitor/**`는 L45-51의 `.authenticated()` 매처 어디에도 매칭되지
  않는다. 따라서 `GET /api/monitor/status`는 **현재 인증 없이 공개**된다.
- 신규 코드는 SecurityConfig를 수정하지 않았다(범위 준수, 정상). 컨트롤러에
  `@SecurityRequirement`를 붙였으나 이는 Swagger 문서 표기일 뿐 실제 필터 인가와 무관하다.
- 위험도: **WARNING**. 관제/운영용 API가 서비스 토폴로지·리소스·내부 포트 정보를 무인증
  노출하므로 운영 환경에서는 정보 노출 위험이 있다.
- **후속조치**: 인증 강제가 필요하면 SecurityConfig에서 `/api/monitor/**`를
  `.authenticated()`(또는 관리자 role)로 이동해야 하며, 이는 **수정 금지 영역이 아닌
  기존 소스 수정**이므로 별도 승인 + 담당(spring-backend-agent, 보안/ devops 협의) 지정이
  필요하다. 본 Loop에서 처리하지 않음.

---

## 수정 요청 대상 AGENT

- 본 Loop 신규 코드에 대한 **필수 수정 요청 없음**(코드 PASS).
- 후속 개선(승인 필요, 선택):
  - 무인증 노출 차단(SecurityConfig `/api/monitor/**` 인가 조정): spring-backend-agent
    (보안 정책은 devops-agent 협의) — 별도 승인 요청서 필요.
  - 순차 폴링 병렬화(지연 격리 강화): spring-backend-agent — 별도 개선 Loop 후보.

---

## 재검증 필요 여부

- 본 Loop 코드: **재검증 불필요**(PASS).
- 위 후속 개선을 착수해 코드를 변경할 경우에만 해당 변경분 재검증 필요.

---

## Loop 02 전체 종료 가능 여부

- Loop A(hana-common DTO) PASS, Loop B(actuator 설정) PASS, **Loop C(aggregator) PASS**로
  Loop 02 본 범위(livecheck + 리소스 모니터링, 재시작 제외)는 **기능적으로 종료 가능**하다.
- 단, 다음 2건을 사용자 판단 항목으로 남긴 채 종료한다:
  1. (WARNING·보안) `/api/monitor/status` 무인증 노출 — 운영 전 SecurityConfig 인가 조정
     여부 결정 필요(별도 승인).
  2. (WARNING·구조) 순차 폴링 최악 지연 합산 — 관제 주기 대비 허용 여부 판단, 필요 시
     병렬화 개선 Loop.
- 재시작 기능은 승인요청서 §0/§10대로 **Loop D**로 이미 분리되어 있어 본 종료 판단과 무관.

**권고**: Loop 02는 PASS로 종료하되, 위 WARNING 2건을 CLAUDE.md §9 "다음 Loop"에
후속항목으로 명시하여 추적할 것.
