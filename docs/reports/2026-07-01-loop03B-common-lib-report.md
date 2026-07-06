# Loop 03-B 작업 보고서 — hana-common 모니터링 공통 계약 확장

- 작성일: 2026-07-01
- Loop: 03-B
- AGENT: spring-backend-agent
- 승인 요청서: `qa/change-requests/2026-07-01-loop03-monitoring-console-fields.md`
- 선행: Loop 03-A (hana-common build.gradle 에 actuator `compileOnly` 추가 + yml `show-details`) 완료

## 1. 작업 목표

콘솔 화면 상단 호스트 카드(CPU%·힙%·디스크%·DB·네트워크)와 서비스별 PID/시작시각을
채우기 위해, hana-common 의 모니터링 DTO 계약을 확장하고 각 서비스의 `/actuator/info`
에 PID/시작시각을 노출하는 공통 InfoContributor 를 auto-configuration 으로 제공한다.

## 2. 범위

- **hana-common/src 하위만** 수정/생성.
- build.gradle / application*.yml / 타 모듈(event/gateway/digitaltwin) 미변경.
- 기존 hana-common DTO 컨벤션(record + lombok `@Builder`) 유지.

## 3. 변경 요약

### 3.1 기존 DTO 확장
- `ServiceMonitor` (record): 기존 필드 순서 유지 후 `Long pid`,
  `OffsetDateTime startedAt` 2개 추가. 수집 실패 시 각각 null. `@Builder` 유지.
- `MonitorSnapshot` (record): 기존 `timestamp`, `services` 유지 후
  `HostSummary host` 추가.

### 3.2 신규 DTO
- `HostSummary` (record + `@Builder`): 상단 호스트 카드 요약.
  - `double cpuUsage` — system.cpu.usage (0.0~1.0, 불가 시 -1.0)
  - `double memoryUsage` — **JVM 힙 사용률 heapUsed/heapMax** (0.0~1.0, 불가 시 -1.0), 호스트 물리메모리 아님
  - `double diskUsage` — (total-free)/total, actuator diskSpace 기반 (0.0~1.0, 불가 시 -1.0)
  - `LiveStatus dbStatus` — actuator db/r2dbc health 기반
  - `LiveStatus networkStatus` — 의존처 도달성 기반
  - Javadoc 에 "0.0~1.0 분수, 프론트에서 ×100", "memoryUsage 는 JVM 힙 사용률" 명시.

### 3.3 PID/시작시각 노출 (auto-configuration)
- `PidInfoContributor` — `InfoContributor` 구현(일반 클래스, lombok 미사용).
  - `ProcessHandle.current().pid()` → `info.process.pid` 항상 노출.
  - `ProcessHandle.current().info().startInstant()` (Optional) → 있을 때만
    ISO-8601 문자열로 `info.process.startTime` 노출 (플랫폼별 empty 안전 처리).
- `MonitorInfoAutoConfiguration` — `@AutoConfiguration` +
  `@ConditionalOnClass(name = "org.springframework.boot.actuate.info.InfoContributor")`,
  `@Bean` 으로 `PidInfoContributor` 등록.
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  신규: `MonitorInfoAutoConfiguration` FQN 1줄 등록.

## 4. 변경 파일 목록

### 수정
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/ServiceMonitor.java`
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/MonitorSnapshot.java`

### 신규
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/HostSummary.java`
- `hana-common/src/main/java/com/mpole/hana/common/monitor/info/PidInfoContributor.java`
- `hana-common/src/main/java/com/mpole/hana/common/monitor/info/MonitorInfoAutoConfiguration.java`
- `hana-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 5. 기술 판단 근거

- **auto-configuration 사용 이유**: hana-common 패키지(`com.mpole.hana.common`)는 각
  서비스의 `@ComponentScan` 대상이 아니므로 `@Component` 로는 빈이 로드되지 않는다.
  Spring Boot 3.x 표준인 `AutoConfiguration.imports` 로 등록해야 각 서비스 컨텍스트에
  자동 로드된다.
- **`@ConditionalOnClass`**: actuator 미보유 서비스에서 클래스 로딩 실패를 피하기 위해
  `InfoContributor` 존재 시에만 활성화.
- **compileOnly actuator**: hana-common 컴파일 시에만 actuator API 를 사용하고 런타임
  actuator 는 각 서비스가 제공. `spring-boot-starter-actuator` 가 transitively
  `spring-boot-autoconfigure`, `spring-boot`, `spring-context` 를 끌어와
  `@AutoConfiguration`, `@ConditionalOnClass`, `@Bean` 도 compileOnly 로 해석됨.
- **startInstant null 안전**: `ProcessHandle.info().startInstant()` 는 플랫폼에 따라
  `Optional.empty` 가능 → `ifPresent` 로 있을 때만 detail 추가.
- IDE 진단(actuate/autoconfigure import 미해결)은 IDE 인덱싱 지연이며, 실제 gradle
  컴파일은 정상 통과.

## 6. 컴파일 검증

- `./gradlew :hana-common:compileJava -q` → **성공**.
- 출력은 기존 Jackson `Include.NON_EMPTY` 관련 annotation-processing warning(5건)
  뿐이며 본 변경과 무관, 에러 없음.

## 7. 다른 AGENT 영향

- **03-C (digitaltwin aggregator)**: digitaltwin 의 모니터링 집계기가 각 서비스의
  - `/actuator/info` 의 `process.pid` / `process.startTime` → `ServiceMonitor.pid` /
    `ServiceMonitor.startedAt` 조립,
  - `/actuator/health` 의 db(또는 r2dbc) / diskSpace 컴포넌트 → `HostSummary.dbStatus`,
    `HostSummary.diskUsage`,
  - `/actuator/metrics` 의 `system.cpu.usage`, `jvm.memory.*`(heap used/max) →
    `HostSummary.cpuUsage`, `HostSummary.memoryUsage`
  로 `ServiceMonitor.pid/startedAt` 및 `HostSummary` 를 조립한다.
- **react-frontend-agent**: `HostSummary` 사용률 필드는 0.0~1.0 분수이므로 프론트에서
  ×100 표시. `memoryUsage` 는 호스트 메모리가 아닌 JVM 힙 사용률임에 유의.
- **devops-agent**: 각 서비스 yml 에 `management.endpoints.web.exposure.include` 로
  `info`, `health`, `metrics` 노출 및 health show-details 필요(03-A 에서 진행/확인).

## 8. QA 요청 항목

1. `ServiceMonitor` / `MonitorSnapshot` record 필드 순서·타입·null 계약 정확성.
2. `HostSummary` 필드 의미/단위(0.0~1.0, -1.0) 및 Javadoc(힙 사용률 명시) 적절성.
3. `PidInfoContributor` 의 startInstant Optional null 안전 처리.
4. `MonitorInfoAutoConfiguration` 의 `@AutoConfiguration` + `@ConditionalOnClass` 조합과
   `AutoConfiguration.imports` 등록 경로/내용 정확성(Spring Boot 3.4.1 표준).
5. hana-common/src 범위 준수(빌드/yml/타 모듈 미변경) 확인.
