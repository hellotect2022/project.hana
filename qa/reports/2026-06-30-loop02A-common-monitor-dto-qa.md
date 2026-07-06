# QA 검증 보고서 — Loop 02-A (hana-common monitor DTO)

- 작성일: 2026-06-30
- Loop: 02-A
- 검증 AGENT: qa-code-reviewer
- 검증 대상: hana-common monitor DTO 신규 4개 파일
- 근거 문서
  - 승인 요청서: `qa/change-requests/2026-06-30-loop02-monitoring-actuator-aggregator.md`
  - 작업 보고서: `docs/reports/2026-06-30-loop02A-common-monitor-dto-report.md`

## 1. 검증 대상 파일
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/LiveStatus.java`
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/ResourceUsage.java`
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/ServiceMonitor.java`
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/MonitorSnapshot.java`

## 2. 전체 판정: **PASS**

승인 요청서 계약과 작업 보고서 기재 내용이 신규 4개 파일과 정확히 일치한다. 기존 코드·빌드·설정
파일은 본 Loop에서 추가 변경되지 않았고, 신규 파일만 추가되었다. 정적 검토상 컴파일·임포트 문제
없음. 다른 모듈에 미치는 영향 없음.

## 3. 항목별 검증 결과

### 항목 1 — 기존 hana-common DTO 컨벤션 준수: **PASS**
- 비교 대상 `dto/DeviceRawPointData.java`, `dto/DeviceBundle.java` 모두 `record + lombok @Builder`
  조합 사용 확인.
- 신규 `ResourceUsage`/`ServiceMonitor`/`MonitorSnapshot` 모두 동일하게 `public record` + `@Builder`
  적용 (`ResourceUsage.java:9-10`, `ServiceMonitor.java:9-10`, `MonitorSnapshot.java:12-13`).
- `LiveStatus`는 상태 열거형이므로 `enum`으로 선언한 것이 타당. record 대상 아님 — 컨벤션 위반 아님.
- 패키지 위치: 기존 DTO는 `com.mpole.hana.common.dto`, 신규는 `com.mpole.hana.common.monitor.dto`
  하위 도메인 패키지로 분리. 모니터링 전용 응답 계약을 별도 네임스페이스로 둔 것으로 합리적이며,
  승인 요청서 §4(나)의 경로(`com/mpole/hana/common/monitor/dto/*.java`)와 일치.
- `@Builder` import 외 불필요한 import 없음. `MonitorSnapshot`만 `OffsetDateTime`/`List` import 추가
  (실제 사용 필드 존재, 정당).
- 참고: lombok은 루트 `build.gradle:24-25`에서 전 subproject에 `compileOnly`+`annotationProcessor`로
  제공되며 `java-library` 플러그인이 `java` 플러그인을 포함하므로 hana-common에서 `@Builder` 정상 적용됨.

### 항목 2 — 필드 구성·타입·null 정책 계약 일치: **PASS**
- `ResourceUsage` 6필드 — 계약(승인서 §4, 보고서 §6) 완전 일치:
  - `heapUsedBytes` long, `heapMaxBytes` long, `processCpuLoad` double(센티넬 -1.0),
    `systemCpuLoad` double(센티넬 -1.0), `threadCount` int, `uptimeMs` long
    (`ResourceUsage.java:11-16`). CPU 측정 불가 시 -1.0 센티넬을 주석으로 명시.
- `ServiceMonitor` — nullable 박스/객체 타입 정책 일치:
  - `service` String, `status` LiveStatus, `responseTimeMs` **Long**(미응답 null),
    `resource` **ResourceUsage**(수집 실패 null), `detail` String(optional)
    (`ServiceMonitor.java:11-15`). 미응답/실패에 null을 담아야 하므로 원시형이 아닌 박스/객체형 선택 정당.
- `MonitorSnapshot` — `timestamp` OffsetDateTime, `services` List<ServiceMonitor>
  (`MonitorSnapshot.java:14-15`). 기존 `DeviceBundle`의 `OffsetDateTime` 사용과 일관.
- `LiveStatus` — UP / DOWN / UNKNOWN 3값 (`LiveStatus.java:7-9`). 승인서의 UP/DOWN 표현에
  UNKNOWN을 추가해 actuator 미응답/판정 불가 상태를 명시적으로 구분 — 계약 위반이 아닌 합리적 보강.

### 항목 3 — 기존 파일·빌드 파일 무수정 (신규만 추가): **PASS**
- `git status --porcelain hana-common/src/` 결과: 추적 변경 0건, untracked는 `monitor/` 패키지뿐.
- `hana-common/build.gradle` 변경 없음(porcelain 출력 없음).
- 본 Loop 산출물은 신규 4개 파일에 한정됨 — 작업 보고서 §4(수정 파일 없음)·§5와 일치.
- 참고(검증 범위 외): 저장소 전체 git status에는 Loop 02-A와 무관한 다수의 사전 변경
  (hana-digitaltwin/hana-event 등)이 존재하나, 이는 본 Loop 시작 시점 스냅샷에 이미 포함된
  기존 변경이며 hana-common 영역과 분리되어 본 검증 대상 아님. hana-common 한정으로는 신규 추가만 존재.
- `hana-common/bin/`(컴파일 산출물)이 untracked로 잡히나 소스 아님 — 판정 영향 없음(빌드 산출물).

### 항목 4 — 컴파일/임포트 정적 검토: **PASS (정적 수준)**
- 4개 파일 모두 패키지 선언 `com.mpole.hana.common.monitor.dto` 정확.
- import: `lombok.Builder`(record 3종), `java.time.OffsetDateTime`/`java.util.List`(MonitorSnapshot)
  — 모두 실제 사용되며 누락/미사용 import 없음.
- 타입 참조: `ServiceMonitor`가 동일 패키지의 `LiveStatus`/`ResourceUsage` 참조,
  `MonitorSnapshot`이 `ServiceMonitor` 참조 — 동일 패키지 내 해소되어 추가 import 불필요. 정상.
- Java 21 toolchain(루트 `build.gradle:17`) 확인 — `record` 문법 지원.
- 빌드 실행 검증은 승인 요청서 방침(actuator 추가 전, Loop B 이후 일괄 빌드)에 따라 생략.
  정적 검토 한정으로 명백한 컴파일 차단 요소 없음.

### 항목 5 — 다른 모듈 영향 없음: **PASS**
- `LiveStatus|ResourceUsage|ServiceMonitor|MonitorSnapshot` 전체 코드 검색 결과 신규 4개 파일에서만
  발견 — 아직 어떤 모듈도 소비하지 않음(Loop C에서 소비 예정).
- hana-common에 신규 클래스만 추가되었고 기존 공개 API 변경 없음 → 의존 모듈 재컴파일/동작 영향 없음.

## 4. WARNING / 참고 사항
- (정보) hana-common `bin/` 디렉터리가 untracked 상태로 노출됨. 빌드 산출물이므로 `.gitignore`
  처리 여부는 별도 정리 대상이나, 본 Loop 판정과 무관.
- (정보) `LiveStatus.UNKNOWN`은 승인서 명시값(UP/DOWN)을 초과하는 보강값이다. Loop C aggregator
  구현 시 UNKNOWN으로의 상태 매핑 규칙(actuator 미응답 vs 응답하나 판정불가)을 명확히 정의할 것.
- (정보) `processCpuLoad`/`systemCpuLoad`의 -1.0 센티넬은 소비측(Loop C)·프론트에서 "측정 불가"로
  해석하는 약속이 코드가 아닌 주석에만 존재한다. Loop C/프론트 계약 문서화 시 명시 권장.

## 5. 수정 요청 대상 AGENT
- 없음. 코드 수정 불요. (위 참고 사항은 후속 Loop B/C 진행 시 고려 항목으로 전달)

## 6. 재검증 필요 여부
- 불필요. 본 Loop 02-A는 PASS로 종료 가능.

## 7. 다음 Loop(B) 진행 가능 여부
- **진행 가능.** hana-common 응답 계약 DTO가 확정되었고 기존 코드 영향이 없으므로,
  Loop B(devops-agent: 3개 모듈 `build.gradle` actuator 의존성 + `application.yml` management 노출,
  승인 게이트)로 진행 가능.
- 단, Loop B는 수정 금지 파일(build.gradle·application.yml) 변경을 포함하므로 사용자 승인 게이트
  통과가 선행되어야 한다(승인 요청서 §10 명시).
