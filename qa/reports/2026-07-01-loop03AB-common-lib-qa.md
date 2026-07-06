# QA 검증 보고서 — Loop 03-A + 03-B 통합

- 작성일: 2026-07-01
- 작성: qa-code-reviewer
- 검증 대상: Loop 03-A(devops, 설정) + Loop 03-B(spring-backend, hana-common 공통 라이브러리)
- 근거 문서
  - 승인 요청서: `qa/change-requests/2026-07-01-loop03-monitoring-console-fields.md`
  - 작업 보고서: `docs/reports/2026-07-01-loop03A-config-report.md`, `docs/reports/2026-07-01-loop03B-common-lib-report.md`
- 검증 방식: 소스/설정 정독 + git diff 범위 확인 + gradle compileJava(read-only 빌드)
- **전체 판정: PASS**

---

## 0. 검증 대상 파일

### 03-A (devops)
- `hana-common/build.gradle` — actuator `compileOnly` 추가
- `hana-event/src/main/resources/application.yml` — `show-details: always`
- `hana-gateway/src/main/resources/application.yml` — `show-details: always`
- `hana-digitaltwin/src/main/resources/application.yml` — `show-details: always`

### 03-B (spring-backend, hana-common/src)
- 수정: `monitor/dto/ServiceMonitor.java`, `monitor/dto/MonitorSnapshot.java`
- 신규: `monitor/dto/HostSummary.java`, `monitor/info/PidInfoContributor.java`, `monitor/info/MonitorInfoAutoConfiguration.java`, `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

> 오탐 방지 컨텍스트 반영: 워킹트리의 Loop 02-B 미커밋 변경(gateway `web-application-type: reactive`, 각 `management:` 블록 신설, event host/kafka 토글 등)은 본 Loop 산출물이 아니므로 판정 근거에서 제외했다. 본 Loop 순수 변경분만 검증했다.

---

## 1. 항목별 검증 결과

### 항목 1 — show-details 3개 yml 문법/들여쓰기, endpoint/endpoints 혼동, gateway 포트 — PASS
- 세 yml 모두 `management.endpoint.health.show-details: always` (단수 `endpoint`) 위치·2-space 들여쓰기 정확.
  - event: `application.yml:50-52`
  - gateway: `application.yml:82-84`
  - digitaltwin: `application.yml:104-106`
- 단수 `endpoint`(개별 엔드포인트 설정)와 복수 `endpoints`(노출 목록)가 각 yml에서 별도 키로 올바르게 분리됨. 혼동 없음.
- gateway: `server.port: 8080`(메인, `application.yml:2`) 유지, `management.server.port: 8090`(`application.yml:76-77`) 유지. 관리 포트 8090 보존 확인.
- git diff 상 03-A 순수 추가분은 event/digitaltwin은 `+ show-details: always` 단일 라인, gateway는 02-B management 블록 내 `endpoint.health.show-details` 하위 블록. 기존 설정값 변경/삭제 없음.
- 참고(비결함): event/digitaltwin yml은 파일 끝 개행 없음(`\ No newline at end of file`). YAML 파싱·기능에 영향 없음. 정보성.

### 항목 2 — build.gradle actuator compileOnly / BOM 버전관리 — PASS
- `hana-common/build.gradle:18-19`: `compileOnly "org.springframework.boot:spring-boot-starter-actuator"` — 버전 미명시.
- `build.gradle:6-11`에서 `spring-boot-dependencies:3.4.1` BOM import(`io.spring.dependency-management`)로 버전 자동 관리 확인. BOM 일관성 준수.
- 스코프 `compileOnly` → 런타임 전이 없음. hana-common은 컴파일에만 actuator API 참조. 각 서비스는 `implementation "...actuator"`(event:9, gateway:9, digitaltwin:11)로 런타임 보유 → 런타임 중복/전이 문제 없음.
- git diff: build.gradle 변경은 주석 1줄 + compileOnly 1줄뿐. 다른 의존성 변경 없음.

### 항목 3 — record 확장(필드 순서/타입/null 계약/보존/@Builder) — PASS
- `ServiceMonitor.java`: 기존 필드(`service, status, responseTimeMs, resource, detail`) 순서 그대로 유지 후 말미에 `Long pid`(18), `OffsetDateTime startedAt`(19) 추가. 참조타입이므로 수집 실패 시 null 계약 성립(Javadoc/주석 명시). `@Builder`(11) 유지. `import java.time.OffsetDateTime` 정상.
- `MonitorSnapshot.java`: 기존 `timestamp, services` 유지 후 `HostSummary host`(16) 추가. `@Builder`(12) 유지.
- 추가 필드를 말미에 배치하여 `@Builder` 사용처 하위호환 유지(빌더는 필드명 기반이므로 순서 무관하나, 위치 컨벤션도 안전).

### 항목 4 — HostSummary 단위 규약 — PASS
- `HostSummary.java` record + `@Builder`(14-15).
- `cpuUsage`(16), `memoryUsage`(17), `diskUsage`(18) 모두 `double`, 0.0~1.0 분수 / 측정 불가 시 `-1.0` 센티넬 규약을 클래스 Javadoc(5-13) + 각 필드 주석에 명시.
- `memoryUsage`가 호스트 물리메모리가 아닌 **JVM 힙 사용률(heapUsed/heapMax)**임을 Javadoc(11-12) + 필드 주석(17)에 굵게 명시. 승인 요청서 §3(메모리% 카드 = JVM 힙% 대체표기)와 일치.
- `dbStatus`(19), `networkStatus`(20)는 `LiveStatus`(UP/DOWN/UNKNOWN) 타입. `LiveStatus.java` enum 3값 확인.
- -1.0 센티넬은 0.0~1.0 정상범위와 명확히 구분되어 프론트에서 "측정 불가" 판별 가능. 규약 적절.

### 항목 5 — PidInfoContributor 구현 정확성 — PASS
- `PidInfoContributor.java:18` `implements InfoContributor`, `@Override public void contribute(Info.Builder builder)`(20-21) — actuator `InfoContributor` 계약 정확.
- `ProcessHandle.current().pid()`(22)로 PID 획득 → `process.put("pid", pid)`(25) 항상 노출.
- `startInstant`(27) `Optional<Instant>` → `ifPresent`(28)로 존재 시에만 ISO-8601 문자열(`instant.toString()`) 추가. 플랫폼별 `Optional.empty` 안전 처리 정확.
- `LinkedHashMap`(24)로 키 순서 보존 후 `builder.withDetail("process", process)`(30). Info.Builder API 사용 정확. lombok 미사용(일반 클래스) — 의도대로.

### 항목 6 — auto-config 정확성(중요) — PASS
- `MonitorInfoAutoConfiguration.java`: `@AutoConfiguration`(16) + `@ConditionalOnClass(name = "org.springframework.boot.actuate.info.InfoContributor")`(17) + `@Bean public PidInfoContributor pidInfoContributor()`(20-22). Spring Boot 3.x 표준 조합 정확.
  - `@ConditionalOnClass(name=...)` 문자열 FQN 사용 → actuator 미보유 서비스에서도 클래스 로딩 실패 없이 안전하게 비활성. 방어적 구현 적절.
- imports 파일: 경로 `hana-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — Spring Boot 3.x 표준 경로 정확. 파일 인코딩 BOM 없음(순수 ASCII), 내용은 `com.mpole.hana.common.monitor.info.MonitorInfoAutoConfiguration` FQN 1줄. 클래스 실제 패키지/이름과 일치.
- **3개 서비스 로드 구조 검증(핵심)**:
  - event/gateway/digitaltwin 모두 `implementation project(":hana-common")` 보유 → hana-common jar의 `AutoConfiguration.imports`가 각 서비스 classpath에 포함.
  - 3개 서비스 모두 `implementation spring-boot-starter-actuator` 런타임 보유 → `@ConditionalOnClass(InfoContributor)` 통과.
  - 결과: 3개 서비스 모두 `MonitorInfoAutoConfiguration` 자동 로드 → `PidInfoContributor` 빈 등록 → `/actuator/info`에 `process.pid`(및 가능 시 `process.startTime`) 노출되는 구조가 맞음.
  - 노출 전제 `management.endpoints.web.exposure.include`에 `info` 포함: event(48-49), gateway(78-81), digitaltwin(100-103) 모두 `health,info,metrics` 확인.

### 항목 7 — 범위 준수(03-B가 hana-common/src만 건드림) — PASS
- git status: 03-B 신규/수정 파일은 모두 `hana-common/src/main/...` 하위(`monitor/` 디렉터리, `resources/META-INF/spring/...`).
- 03-B가 build.gradle / application*.yml / 타 모듈(event/gateway/digitaltwin) 소스를 건드린 흔적 없음. (build.gradle·yml은 03-A devops 소관, git diff로 03-A 범위 내 변경만 확인.)
- 워킹트리의 그 외 digitaltwin `.java` 변경들은 별개 작업(Loop 02-B 등)으로 본 Loop과 무관.

### 항목 8 — 컴파일 검증 — PASS
- `./gradlew :hana-common:compileJava -q` → **EXIT=0**.
- `./gradlew :hana-event:compileJava :hana-gateway:compileJava :hana-digitaltwin:compileJava -q` → **EXIT=0** (다운스트림 영향 없음 확인).
- actuator compileOnly 추가 및 DTO/InfoContributor/auto-config 신설이 4개 모듈 컴파일에 회귀 없음.

---

## 2. 종합 판정

| 항목 | 결과 |
|---|---|
| 1. show-details yml 문법/포트 | PASS |
| 2. build.gradle actuator compileOnly/BOM | PASS |
| 3. record 확장 계약/보존/@Builder | PASS |
| 4. HostSummary 단위 규약 | PASS |
| 5. PidInfoContributor 구현 | PASS |
| 6. auto-config 정확성(3서비스 로드) | PASS |
| 7. 03-B 범위 준수 | PASS |
| 8. 컴파일(공통+다운스트림) | PASS |

**전체 판정: PASS**

- 코드 수정 요청 대상 AGENT: 없음(수정 요청서 불필요).
- 재검증 필요 여부: 불필요.
- **03-C 진행 가능 여부: 가능.** 03-C(digitaltwin aggregator 확장)가 소비할 계약(`ServiceMonitor.pid/startedAt`, `HostSummary`, `/actuator/info`의 `process.pid`, health show-details의 db/diskSpace)이 모두 준비 완료됨.

---

## 3. 후속 항목 (비차단, 위험도 기록)

1. **[MEDIUM] show-details: always 무인증 노출**
   - 3개 서비스 `/actuator/health`가 인증/인가 없이 DB(db/r2dbc)·디스크(diskSpace) 상세를 노출. 현 `/api/**` 개방 posture와 동일 선상이나, 운영 배포 전 actuator 엔드포인트 접근 제어(네트워크 분리 또는 management 포트 시큐리티) 정책 확정 필요. 승인 요청서 §8·03-A 보고서 §11과 동일 위험. devops-agent 후속 검토 대상.
2. **[LOW] event/digitaltwin yml 파일 끝 개행 부재**
   - 기능 영향 없음. 정보성. 필요 시 devops 정리.
3. **[정보] 03-C 소비 계약 주의**
   - `HostSummary.memoryUsage`는 호스트 물리메모리가 아닌 JVM 힙 사용률. 프론트 표기(memoryUsage/×100)와 aggregator 조립 시 힙 기준임을 유지할 것. react-frontend-agent 전달 필요.

---

## 4. 검증에 사용한 read-only 명령
- `git status --short`, `git diff <파일>` (범위 확인)
- `./gradlew :hana-common:compileJava -q` → EXIT=0
- `./gradlew :hana-event:compileJava :hana-gateway:compileJava :hana-digitaltwin:compileJava -q` → EXIT=0
- imports 파일 BOM/내용 바이트 확인, 각 모듈 build.gradle 의존성 grep

(본 QA는 검증 전용으로 어떤 코드·설정·문서도 수정하지 않았다. 산출물은 본 보고서뿐이다.)
