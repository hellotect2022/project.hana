# QA 검증 보고서 — Loop 03-C: digitaltwin aggregator 필드 확장

- 작성일: 2026-07-01
- Loop: 03-C
- 검증자: qa-code-reviewer
- 검증 대상:
  - `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/monitor/service/MonitorService.java`
  - `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/monitor/api/MonitorController.java`
- 근거 문서:
  - 승인 요청서 `qa/change-requests/2026-07-01-loop03-monitoring-console-fields.md` (사용자 승인 2026-07-01)
  - 작업 보고서 `docs/reports/2026-07-01-loop03C-aggregator-fields-report.md`
  - 선행 QA `qa/reports/2026-07-01-loop03AB-common-lib-qa.md`

## 전체 판정: PASS

승인 범위(digitaltwin aggregator 확장) 내에서 요구 필드가 모두 구현되었고, 예외/파싱 방어,
리소스 정리, 하위호환, 컴파일, 범위 준수가 모두 확인되었다. 코드 수정 없이 PASS.
후속 항목(무인증 노출, network-targets 운영값, 순차 폴링 지연, memoryUsage 힙 라벨)은
Loop 03 종료를 막지 않는 운영/UI 개선 항목으로 아래에 기록한다.

---

## 항목별 검증 결과

### 1. pid/startedAt 분기 정확성 — PASS
- `pollService()`(MonitorService.java:95-144)에서 분기별 세팅 확인:
  - health 폴링 예외: `pid=null, startedAt=null` (:112-113).
  - health status != UP(DOWN/UNKNOWN): `pid=null, startedAt=null` (:128-129).
  - UP: `collectProcessInfo()` 호출 후 `pid/startedAt` 세팅 (:134,141-142).
- `collectProcessInfo()`(:199-219): UP일 때만 호출되므로 "UP일 때만 /actuator/info 호출" 조건 충족.
  info 수집/파싱 실패 시 `ProcessInfo.EMPTY(null,null)` 반환하고 status에 영향 없음 → "두 필드만
  null, status 유지" 분기 정확.
- 타입/널 방어:
  - `asLong()`(:426-438): Number/String 모두 처리, 실패 시 null. `process.pid`는 PidInfoContributor가
    `long`으로 노출(PidInfoContributor.java:22-25) → 정합.
  - `asOffsetDateTime()`(:441-450): String이 아니거나 blank면 null, `DateTimeParseException` catch로 null
    반환. `process.startTime`은 `Instant.toString()`(UTC/ISO-8601, PidInfoContributor.java:28) →
    `OffsetDateTime.parse` 직접 호환 확인.
  - `processObj instanceof Map` 패턴매칭(:209)으로 형변환 안전.
- 키 정합성: 파서 키(`process`/`pid`/`startTime`)가 PidInfoContributor 노출 키와 일치.

### 2. HostSummary.memoryUsage(힙 태그) — PASS
- `collectHeapUsage()`(:247-254): `taggedMetricValue(..., "jvm.memory.used", "area:heap", ...)`,
  `jvm.memory.max` 동일. `taggedMetricValue()`(:353-356)가 URI에 `?tag=area:heap`를 실제로 부착.
  → 태그 적용 확인(nonheap 합산 왜곡 방지).
- 센티넬: `heapUsed < 0 || heapMax <= 0` 이면 `-1.0` 반환(:250-251). max<=0/실패 방어 정확.
- 참고: RestClient `.uri(String)`는 URI 템플릿으로 처리되나 템플릿 변수({}) 없이 `tag=area:heap`의
  콜론은 query 컴포넌트에서 유효 문자로 그대로 전달됨. 컴파일/문법 문제 없음.

### 3. diskUsage 안전 접근 — PASS
- `collectDiskUsage()`(:260-276): `nestedMap(health,"components","diskSpace","details")`로 중첩 안전 탐색,
  중간이 Map 아니면 null(:461-475). details/total/free 부재 시 `-1.0`(:262-263,267-268).
- `total <= 0` 0-division 방어(:267). 계산 후 `used<0 || used>1.0` 범위 이탈도 센티넬 처리(:272-273).
- `show-details: always`가 3개 yml에 존재(digitaltwin application.yml:106, event:52, gateway:84)하여
  diskSpace.details 노출 전제 충족.

### 4. dbStatus 매핑 — PASS
- `collectDbStatus()`(:282-299): components에서 `db` 우선, 없으면 `r2dbc`(:287-290). digitaltwin(JPA)은
  `db` 컴포넌트 → 정합. 컴포넌트/status 부재 시 `UNKNOWN`(:284,292,295). "UP" 아니면 DOWN 매핑.
- 다만 self는 digitaltwin(JPA)이므로 실제로는 항상 `db` 경로. `r2dbc` 분기는 방어용으로 무해.

### 5. networkStatus(TCP 도달성) — PASS
- `isReachable()`(:318-325): `try (Socket socket = new Socket())` try-with-resources로 누수 방지.
  `connect(target, 1000ms)` connect timeout 1초(:42,320).
- 판정: 빈 목록 UNKNOWN(:306-307), 하나라도 실패 DOWN(:310-312), 전부 도달 UP(:315). 요구와 일치.
- host:port 파싱 `parseNetworkTargets()`(:398-423): 빈 문자열/blank 방어, `lastIndexOf(':')` 사용(IPv6는
  미고려하나 대상이 IPv4:port라 무해), 콜론 위치/포트 파싱 실패 항목은 warn 로그 후 skip.
  `createUnresolved`로 DNS 조회를 connect 시점까지 지연.
- `@Value` 기본값(:57): `192.168.250.181:5432,192.168.250.249:9092`(postgres/kafka). yml override 없음
  확인(application.yml에 `hdt.monitor.network-targets` 미존재) → 코드 기본값 적용.

### 6. 장애 격리 / 지연 — PASS (지연 특성은 후속 항목으로 기록)
- `SimpleClientHttpRequestFactory` connect/read timeout 2초(:65-67)가 단일 `restClient`에 적용되어
  info/health/metrics 모든 HTTP 호출에 공통 적용. self 호출도 동일 restClient 사용 → self에도 timeout 적용됨.
- 네트워크 도달성은 대상당 1초 connect timeout.
- 순차 누적: `getSnapshot()`이 3개 서비스 순차 폴링 + self HostSummary 순차 조립. 각 서비스가 health(2s)
  + metrics 다수(각 2s) + info(2s), host가 metrics 2회 + health(2s) + network(대상당 1s) 를 모두 직렬
  호출하므로 최악의 경우(다수 대상 timeout) 응답이 수십 초까지 누적될 수 있음. 한 호출 지연이 전체
  blocking 지속시간에 선형 가산됨. → 정상 경로에서는 문제없으나 장애 상황 지연은 후속 개선 항목(아래 4).

### 7. 범위 준수 — PASS
- `git status`상 변경은 두 대상 파일이 속한 신규 디렉터리
  `hana-digitaltwin/src/main/java/.../monitor/`(untracked)와 03-B 산출물
  `hana-common/.../monitor/`(untracked)뿐. 본 Loop 대상 파일은 모두 `hana-digitaltwin/src` 내.
- yml / build.gradle / SecurityConfig / hana-common은 03-C에서 무변경(03-A/03-B에서 이미 처리된
  상태를 소비만 함). SecurityConfig(`/api/**`,`/actuator/**` permitAll)는 미수정.
- (참고) `git diff --stat`에 나타나는 device/iam/location/unityAsset 등 기타 변경은 본 Loop와 무관한
  선행 작업분으로, 03-C 산출물이 아님.

### 8. 응답 하위호환 — PASS
- `ServiceMonitor`(ServiceMonitor.java): 기존 필드(service/status/responseTimeMs/resource/detail) 유지 +
  `pid`, `startedAt` 추가만.
- `MonitorSnapshot`: 기존 timestamp/services 유지 + `host` 추가만.
- `ResourceUsage`, `LiveStatus` 무변경. 컨트롤러 경로/시그니처/`ApiResponse` 래퍼 유지, `@Operation`
  설명만 갱신(MonitorController.java:31-40). → 하위호환 유지.

### 9. 컴파일 — PASS
- `./gradlew :hana-digitaltwin:compileJava -q` → EXIT=0(오류 없음).

---

## 수정 요청 대상 AGENT
- 없음(코드 수정 불필요). PASS.

## 재검증 필요 여부
- 불필요. 코드 수정이 없으므로 재검증 대상 없음.

---

## Loop 03 전체 종료 가능 여부: 가능

- 03-A(devops: build.gradle actuator compileOnly + yml show-details) PASS.
- 03-B(hana-common DTO 확장 + PidInfoContributor auto-config) PASS.
- 03-C(digitaltwin aggregator 확장) PASS.
- 승인 요청서(4장)의 (가)/(나)/(다) 항목이 모두 반영되고 화면 요구 필드(PID/시작시각/CPU/힙메모리/
  디스크/DB/네트워크)가 스냅샷에 채워짐. Loop 03 정상 종료 가능(WARNING 없음).

## 후속 항목 (Loop 03 종료를 막지 않음)

1. **무인증 노출 (MEDIUM)**: SecurityConfig에서 `/api/**`, `/actuator/**`가 `permitAll()`
   (SecurityConfig.java:53-54)이라 `/api/monitor/status` 및 health/info/metrics가 무인증 접근 가능.
   `show-details: always`로 db/diskSpace 상세도 노출됨(승인서 8장 명시 위험과 동일 선상).
   컨트롤러 주석("JWT 인증 필터를 그대로 통과해야 한다", MonitorController.java:19)은 실제 posture와
   불일치 → 운영 배포 전 접근통제(별도 Loop) 및 주석/명세 정합화 권장.
2. **network-targets 운영값**: 코드 기본값이 특정 사설 IP(postgres 192.168.250.181:5432,
   kafka 192.168.250.249:9092)로 하드코딩됨. 환경별로 다르므로 운영 배포 시 yml
   `hdt.monitor.network-targets` override(devops 승인) 필요. 미override 시 오탐(DOWN) 가능.
3. **순차 폴링 지연**: 다수 대상이 동시 timeout이면 blocking 시간이 선형 누적(항목 6). 콘솔 새로고침
   응답이 장애 시 수십 초까지 지연 가능 → 병렬 폴링/전체 상한 timeout/캐싱(짧은 TTL) 후속 검토.
4. **memoryUsage=힙 UI 라벨**: `host.memoryUsage`는 호스트 물리메모리가 아닌 JVM 힙 사용률
   (HostSummary.java:11-17에 명시). 프론트 라벨/툴팁에 "JVM 힙"임을 반영하고, 0~1 분수 ×100 표기,
   -1.0은 "측정 불가" 처리 필요(react-frontend-agent 책임).
