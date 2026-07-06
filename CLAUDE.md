# CLAUDE.md — Hana DigitalTwin 플랫폼 총괄 오케스트레이터

이 파일은 **Hana DigitalTwin 플랫폼 전체(백엔드 + 프론트엔드)** 를 총괄하는 메인 운영
규칙이다. 이 저장소(`05-02.hanadream`, 백엔드)가 **오케스트레이터 본부(HQ)** 이며,
프론트엔드 저장소(`05-06.hanadream_web_new`)는 경량 위성 설정으로 이 규칙을 따른다.

---

## 1. 플랫폼 개요

Hana DigitalTwin 은 **건물/설비 디지털트윈 관제 플랫폼**이다.

| 구성 | 저장소(경로) | 스택 |
|---|---|---|
| 백엔드 (본부) | `d:\HDD\05-02.hanadream` | Java 21, Spring Boot 3.4.1, Gradle 멀티모듈 |
| 프론트엔드 (위성) | `d:\HDD\05-06.hanadream_web_new` | React 19, Vite 8, TypeScript 5.7, Three.js |

두 저장소는 **별도 git repo** 이지만 **API 계약·도메인 모델로 강하게 결합**된 하나의
제품이다. 두 저장소 모두 Claude 워크스페이스로 열려 있어 양쪽 `CLAUDE.md`가 함께
로드된다.

### 백엔드 모듈 (settings.gradle)
- `hana-common` — 공통 라이브러리(java-library)
- `hana-gateway` — WebFlux/R2DBC API 게이트웨이 (MSSQL + Postgres, reactor-kafka)
- `hana-event` — WebFlux/R2DBC 이벤트 스트림 (Postgres, reactor-kafka)
- `hana-digitaltwin` — 메인 API (WebMVC, JPA, Security/JWT, Kafka, AMQP) ← **주 작업 모듈**

### 도메인 (digitaltwin)
device · location · patrol · sop · iam · event · realtime · buildingLighting ·
unityAsset · audit · system · systemMenu · integration

### 프론트엔드 구조
`pages/`가 백엔드 도메인과 1:1 대응, `services/`가 도메인별 API 클라이언트, `viewer/`가
Three.js 3D 씬(scene/state/lib/hooks).

---

## 2. 최우선 원칙 (절대 규칙)

1. **절대 바로 코드를 수정하지 않는다.** 검증 → 문제 분석 → 수정 승인 요청서 작성 →
   사용자 승인 → 담당 AGENT가 자기 소유 영역만 수정 → 작업 보고서 → QA 재검증.
2. **QA 검증 없이 다음 Loop로 넘어가지 않는다.**
3. **보고서 없이 다음 Loop로 넘어가지 않는다.**
4. **각 AGENT는 자기 소유 영역만 직접 수정한다.** 타 영역 문제는 변경 요청서로 전달.
5. **반복되는 기술 절차는 Skill로 분리한다.**

운영 체계 파일(`.claude/`, `docs/`, `qa/`, `CLAUDE.md`) 생성은 승인 없이 허용된다.
기존 소스코드·설정·의존성·빌드 파일 수정은 항상 승인이 필요하다.

---

## 3. AGENT 운영 방식

사용자는 개별 터미널로 AGENT를 직접 띄우지 않는다. **메인 Claude 세션 하나가
오케스트레이터**로서 작업 목표에 맞는 AGENT를 선택·호출한다. 사용자가 특정 AGENT를
지정하면 그 AGENT를 우선 사용한다.

### AGENT별 소유 영역

| AGENT | 직접 관리(수정 가능) | 읽기/검증 | 위치 |
|---|---|---|---|
| `loop-orchestrator` | `CLAUDE.md`, `docs/reports/`, `qa/change-requests/` | 전체 | 백엔드 |
| `docs-architect` | `docs/`, `README.md` | 전체 코드 | 백엔드 |
| `qa-code-reviewer` | `qa/` | 전체 (검증 전용) | 백엔드 |
| `integration-architect` | `docs/integration/`, `docs/architecture/`, `docs/reports/` | 전체 | 백엔드 |
| `skill-curator` | `.claude/skills/`, `docs/reports/` | 전체 | 백엔드 |
| `spring-backend-agent` | `hana-common/src/`, `hana-event/src/`, `hana-gateway/src/`, `hana-digitaltwin/src/` | 전체 | 백엔드 |
| `database-agent` | `**/src/main/resources/db/`, `hana-digitaltwin/scripts/`, DB 스키마/마이그레이션 | 엔티티/리포지토리 | 백엔드 |
| `devops-agent` | `**/application*.yml`, 빌드/배포 설정 (전부 **승인 게이트**) | 전체 | 백엔드 |
| `react-frontend-agent` | `05-06.../src/` | 백엔드 API 계약 | 프론트 |

소유 영역이 불명확하면 코드를 수정하지 말고 먼저 사용자에게 영역 분리안을 보고한다.

### 침범 금지
각 AGENT는 자기 영역만 수정한다. 타 영역에 수정이 필요하면
`qa/change-requests/`에 **AGENT 변경 요청서**를 작성한다.

---

## 4. 승인 기반 Bounded Loop Coding

```
1. 작업 목표 정의 (작은 단위)
2. 담당 AGENT 선택
3. 관련 Skill 확인
4. 작업 계획 작성
5. 사용자 승인 필요 여부 판단
6. 승인 불필요한 문서/보고서/검증 작업 수행
7. 코드 수정 필요 시 → 수정 승인 요청서 작성
8. 사용자 승인 후 담당 AGENT가 자기 영역만 수정
9. 작업 보고서 작성
10. qa-code-reviewer 검증 → QA 보고서
11. PASS / WARNING / FAIL 판단
12. PASS → 다음 Loop / WARNING → 위험도 기록 후 사용자 판단 / FAIL → 승인 요청서 후 대기
```

### Loop 종료 조건
QA PASS · WARNING(위험도+후속항목 기록) · 사용자 중단 지시 · 동일 문제 3회 반복(원인 분석
보고서 작성, 임의 수정 중단).

상세 절차는 `.claude/skills/loop-coding/SKILL.md`, 승인 게이트는
`.claude/skills/approval-gate/SKILL.md` 참조.

---

## 5. QA Gate 규칙

- **PASS**: 코드 수정 없음, 보고서 저장, 다음 Loop 진행 가능.
- **WARNING**: 문제·위험도 기록, 사용자에게 수정 여부 확인, 승인 전 수정 금지.
- **FAIL**: 다음 Loop 금지, QA 보고서 + 수정 승인 요청서 작성, 승인 후에만 담당 AGENT가
  자기 영역 수정, 수정 후 재검증.

QA 보고서 양식·절차는 `.claude/skills/qa-code-review/SKILL.md` 참조.

---

## 6. 보고서 규칙

모든 AGENT는 작업/검증 후 보고서를 남긴다. 양식은
`.claude/skills/report-writing/SKILL.md` 참조.

| 보고서 | 위치 |
|---|---|
| AGENT 작업 보고서 | `docs/reports/` |
| QA 검증 보고서 | `qa/reports/` |
| 코드 수정 승인 요청서 / AGENT 변경 요청서 | `qa/change-requests/` |
| 아키텍처·연동 문서 | `docs/architecture/`, `docs/integration/` |

파일명 규칙: `YYYY-MM-DD-loopNN-<요약>.md`

---

## 7. 수정 금지 파일 (승인 필수)

다음은 모든 AGENT가 임의 수정 금지. 사용자가 명확히 승인하면 담당 AGENT만 수정한다.

```
.env  .env.*
build.gradle  build.gradle.kts  settings.gradle  settings.gradle.kts  gradle.properties
gradlew  gradlew.bat  gradle/  gradle.lockfile
**/application*.yml  (devops-agent + 승인)
docker-compose.yml  Dockerfile
package.json  package-lock.json  tsconfig.json  vite.config.ts  (프론트, devops + 승인)
.github/
```

---

## 8. 등록된 Skill

| Skill | 용도 |
|---|---|
| `loop-coding` | 승인 기반 Loop 진행 절차 |
| `report-writing` | 작업/QA/요청서 양식 |
| `qa-code-review` | QA 검증 체크리스트 |
| `approval-gate` | 코드 수정 승인 게이트 |
| `agent-boundary-control` | AGENT 영역 침범 통제 |
| `spring-boot-api` | Spring Boot API 작성 표준 |
| `database-migration` | DB 스키마/마이그레이션 절차 |
| (프론트) `react-component` | React 컴포넌트 작성 표준 |

Skill 분리 기준·신규 Skill 작성은 `skill-curator` AGENT가 관리.

---

## 9. 현재 상태 / 다음 Loop

- **Loop 01 (완료)**: 프로젝트 분석 + AGENT/Skill/docs/qa/CLAUDE.md 운영 체계 세팅.
  - 작업 보고서: `docs/reports/2026-06-29-loop01-setup-report.md`
  - QA 보고서: `qa/reports/2026-06-29-loop01-setup-qa.md`
- **Loop 02 (완료)**: 3개 서비스 livecheck + 리소스 모니터링 (재시작 제외). 순수 Actuator + digitaltwin aggregator 구조.
  - 승인 요청서: `qa/change-requests/2026-06-30-loop02-monitoring-actuator-aggregator.md`
  - 02-A hana-common 공통 DTO(`monitor.dto`): 보고서 `docs/reports/2026-06-30-loop02A-common-monitor-dto-report.md` / QA `qa/reports/2026-06-30-loop02A-common-monitor-dto-qa.md` (PASS)
  - 02-B 3개 모듈 Actuator 도입(build.gradle + yml, gateway는 reactive 전환 + 전용 management 포트 8090): 보고서 `docs/reports/2026-06-30-loop02B-actuator-config-report.md` / QA `qa/reports/2026-06-30-loop02B-actuator-config-qa.md` (PASS)
  - 02-C digitaltwin aggregator `GET /api/monitor/status`: 보고서 `docs/reports/2026-07-01-loop02C-digitaltwin-aggregator-report.md` / QA `qa/reports/2026-07-01-loop02C-digitaltwin-aggregator-qa.md` (PASS)
  - **후속 항목(추적)**:
    - (보안) `/api/monitor/status` 무인증 공개 — 사용자 결정으로 현상태 유지. 프로젝트 전반의 `/api/** 전체 permitAll` posture 재검토는 별도 보안 Loop.
    - (성능) aggregator 순차 폴링 → 지연 합산. 필요 시 병렬화(별도 개선 Loop).
    - (운영) gateway reactive 전환 후 8080(빈 서버)+8090 동반 기동 실기동 1회 확인.
- **Loop 03 (완료)**: 서버 관리 콘솔 화면용 필드 확장 — 서비스별 PID/시작시각 + 상단 호스트 카드(HostSummary: CPU·힙%·디스크·DB·네트워크). Actuator 범위 내(OSHI 미도입).
  - 승인 요청서: `qa/change-requests/2026-07-01-loop03-monitoring-console-fields.md`
  - 03-A 설정(hana-common actuator compileOnly + yml show-details:always ×3): 보고서 `docs/reports/2026-07-01-loop03A-config-report.md`
  - 03-B hana-common 확장(ServiceMonitor pid/startedAt, HostSummary, PidInfoContributor auto-config): 보고서 `docs/reports/2026-07-01-loop03B-common-lib-report.md`
  - 03-A+B QA: `qa/reports/2026-07-01-loop03AB-common-lib-qa.md` (PASS)
  - 03-C digitaltwin aggregator 확장(info/health/metrics 파싱 + TCP 도달성): 보고서 `docs/reports/2026-07-01-loop03C-aggregator-fields-report.md` / QA `qa/reports/2026-07-01-loop03C-aggregator-fields-qa.md` (PASS)
  - **후속 항목(추적)**:
    - (보안) `/api/monitor/status`·`/actuator/**` 무인증 노출 유지(사용자 결정). 컨트롤러 주석 "JWT 인증"이 실제 posture와 불일치 → 정합화 + 접근통제는 별도 보안 Loop.
    - (운영) `hdt.monitor.network-targets` 사설 IP 하드코딩 기본값 → 운영 시 yml override(devops 승인).
    - (성능) aggregator 순차 폴링 지연 누적 → 병렬화/캐싱 검토.
    - (프론트) `host.memoryUsage`는 호스트 물리메모리 아닌 JVM 힙 사용률 → UI 라벨/툴팁, ×100, -1.0="측정 불가", UNKNOWN 처리 (react-frontend-agent).
    - (프론트) 화면 표시명 hdt-* ↔ 실제 hana-event/gateway/digitaltwin 매핑.
- **Loop 04 (완료)**: 모니터링 보안 강화 — `/api/monitor/status` admin JWT(`hasRole("ADMIN")`) 보호 + raw actuator localhost 바인딩. (Loop 02-C 무인증 결정 번복)
  - 승인 요청서: `qa/change-requests/2026-07-01-loop04-monitor-admin-auth.md`
  - 04-A SecurityConfig(`/api/monitor/**` ADMIN, permitAll 앞): `docs/reports/2026-07-01-loop04A-security-report.md`
  - 04-B actuator 전용 localhost 관리포트(event 8091 / gateway 8090 / digitaltwin 8085, 127.0.0.1): `docs/reports/2026-07-01-loop04B-actuator-bind-report.md`
  - 04-C aggregator 폴링 기본포트 갱신(8091/8090/8085): `docs/reports/2026-07-01-loop04C-aggregator-port-report.md`
  - 통합 QA: `qa/reports/2026-07-01-loop04-security-qa.md` (PASS)
  - 참고 문서(Notion): "Spring Boot Actuator 완전 정리 — 설정·동작원리·모니터링 취합" https://app.notion.com/p/3907df5d44f681fabb23d9f02eff7bca
  - **후속 항목(추적)**:
    - (배포) localhost 바인딩은 aggregator+3서비스 **동일 호스트 전제** → 분산 배포 시 재설계.
    - (배포) 방화벽/컨테이너에서 관리포트 8085·8091 외부 차단 이중 유지.
    - (프론트) "서버 관리 콘솔" ADMIN 라우트 가드 + privateApi(ADMIN 토큰)로 호출.
    - (정리) MonitorController 스테일 주석("SecurityConfig 미수정") 정합화, Loop 단위 커밋 분리.
- **Loop 05 (완료)**: 프론트 "서버 관리 콘솔"(`/system/server`) 연동 — `GET /api/monitor/status` 소비, 상단 카드 5개 + 서비스 테이블 + 5초 폴링 + 페이지레벨 ADMIN 가드/403 처리. (프론트 저장소 05-06)
  - 승인 요청서: `qa/change-requests/2026-07-01-loop05-frontend-server-console.md`
  - 작업 보고서: `docs/reports/2026-07-01-loop05-frontend-console-report.md` / QA: `qa/reports/2026-07-01-loop05-frontend-console-qa.md` (PASS)
  - 변경(05-06/src): 신규 `services/monitorService.ts`, 수정 `pages/system/SystemServerPage.tsx`. role 필드=`roles`(복수) 확정.
  - **후속 항목(추적)**:
    - (F-1, WARNING) `Sidebar.tsx` `user?.role?.[0]` → `user?.roles?.[0]` 필드 오류(기존 버그, 범위 밖). react-frontend-agent.
    - (F-2, WARNING) `/system/server` 라우트레벨 ADMIN 가드 부재(현재 컴포넌트 가드+서버 403 이중 방어로 노출 위험 없음). react-frontend-agent.
    - (F-3, INFO) 재시작 버튼용 백엔드 제어 엔드포인트 없음(현재 disabled). → Loop D.
    - (정리) 프론트 작업트리에 타 도메인 미커밋 40여 건 공존 → Loop 05 변경만 분리 커밋 권고.
- **Loop 06 (완료)**: 모니터링 서비스별 관점 재설계 — 상단 카드=인프라 도달성(DB/Kafka), 테이블=서비스별 CPU/힙/디스크, 서비스명 클릭 상세.
  - 승인 요청서: `qa/change-requests/2026-07-01-loop06-per-service-monitoring.md`
  - 06-A DTO(ResourceUsage+diskUsage, DependencyStatus 신규, MonitorSnapshot host→dependencies, HostSummary 삭제): `docs/reports/2026-07-01-loop06A-dto-redesign-report.md`
  - 06-B MonitorService(dependencies 조립=`hdt.monitor.dependencies` **콤마 문자열** name=host:port, 서비스별 diskUsage): `docs/reports/2026-07-01-loop06B-monitorservice-report.md`
  - 06-C 프론트(인프라 카드 + 서비스별 컬럼 + 클릭 아코디언): `docs/reports/2026-07-01-loop06C-frontend-redesign-report.md`
  - 통합 QA: `qa/reports/2026-07-01-loop06-per-service-qa.md` (PASS)
  - **후속 항목(추적)**:
    - (설정) `hdt.monitor.dependencies`/`targets`는 **`@Value` 기반이라 YAML 리스트 아닌 콤마 문자열**로 써야 함. 리스트/Map 문법 원하면 `@ConfigurationProperties` 리팩터 필요. TCP 포트는 0~65535(예: 8091/8090/8085/5432).
    - (정리) `application.yml` 잔존 `hdt.monitor.network-targets`(코드 미사용) 정리 — devops.
    - (문서) `MonitorController @Operation` 설명이 제거된 host 요약을 서술(노후화) → 정합화.
    - (인프라) DB/Kafka CPU/디스크까지 필요 시 node_exporter 등 도입은 별도 검토.
- **Loop 07 (완료)**: 디바이스 활성/비활성(active) 일괄 처리 — 전체/카테고리/개별을 단일 bulk 엔드포인트 `PATCH /api/device/active`(scope=ALL|CATEGORY|DEVICES)로 통일.
  - 승인 요청서: `qa/change-requests/2026-07-01-loop07-device-active-bulk.md`
  - 07-A 백엔드(BulkActiveRequest, DeviceRepository @Modifying 3종+updatedAt 세팅, DeviceService.updateActiveBulk, DeviceController): `docs/reports/2026-07-01-loop07A-device-active-bulk-report.md` / QA `qa/reports/2026-07-01-loop07A-device-active-bulk-qa.md` (PASS)
  - 07-B 프론트(patchDeviceActiveBulk, useSetActiveBulk, DeviceSidebar 전체/카테고리 버튼+개별 이관): `docs/reports/2026-07-01-loop07B-frontend-bulk-active-report.md` / QA `qa/reports/2026-07-01-loop07B-frontend-bulk-active-qa.md` (PASS)
  - 핵심: bulk JPQL은 `@PreUpdate` 우회 → 쿼리에서 `updatedAt` 명시 세팅. 카테고리는 `resolveSmallCategoryIdsForFilter` 재활용. 단일 소분류=scope CATEGORY, 상위/혼합=deviceIds 수집 후 DEVICES 폴백.
  - **후속 항목(추적)**: 단건 `patchDeviceActive`/`PATCH /device/{id}` active-only 경로는 호출부 0(모달 전체편집용 잔존) — 미도입 확정 시 제거 검토. 카테고리 대량 DEVICES 폴백 시 IN 절 청크 분할 검토. 개별 ActiveBtn busy disabled 미적용(경미).
- **Loop 08 (완료)**: 포인트 정보 매핑 — 상단 통계 카드(전체/완료/매핑률/미매핑) + 엑셀 양식 다운로드/파일 업로드(ref 코드 일괄 수정, Apache POI).
  - 승인 요청서: `qa/change-requests/2026-07-01-loop08-point-mapping-excel.md`
  - 08-A build.gradle poi-ooxml:5.2.5 추가: `docs/reports/2026-07-01-loop08A-poi-dependency-report.md`
  - 08-B 백엔드 엑셀(GET .../mapping/template, POST .../mapping/import, GET .../mapping/stats; DevicePointMappingExcelService; 기존 updateDevicePointRefMappings 재활용): `docs/reports/2026-07-01-loop08B-excel-endpoints-report.md` / QA `qa/reports/2026-07-01-loop08B-excel-endpoints-qa.md` (WARNING=무관 기존변경 오탐, 엑셀 산출물 PASS)
  - 08-C 프론트(통계 카드 + 양식/업로드 버튼, 기존 필터·테이블·저장 무변경): `docs/reports/2026-07-01-loop08C-frontend-cards-excel-report.md` / QA `qa/reports/2026-07-01-loop08C-frontend-cards-excel-qa.md` (PASS)
  - **후속 항목(추적)**: 대용량 xlsx는 SXSSF 스트리밍 + 업로드 크기/행 상한(zip-bomb 방어) MEDIUM. stats 카드는 categoryId+keyword 범위만 집계(deviceId 미반영) — 장비 선택 시 카드/목록 범위 상이, UX 확인. import 전건 롤백 정책 문서화. 미사용 findByPointKey 정리.
  - **주의(오탐)**: 08-B QA가 지적한 DeviceCategoryCascadeService.java / DeviceLocationResponse.java 변경은 세션 시작 전부터 있던 사용자 기존 미커밋 변경(우리 작업 아님). 커밋 시 Loop별 분리 권고.
- **알람 정책 재설계 (진행 중)**: 설계 문서 `docs/architecture/2026-07-01-alarm-policy-redesign.md`. 카테고리+tagName 런타임 평가, AI 임계값/DI 토글, 레벨 3단(주의/경계/심각), 평가=hana-event 스트림.
  - **Loop 09 (완료, Phase 1+2)**: 신규 alarm 도메인 CRUD — AlarmPolicy(+Level) 엔티티/enum/Repo/DTO/Service/Controller(`/api/alarm/policies`), ddl-auto 자동생성. 기존 event/device 무변경. 보고서 `docs/reports/2026-07-02-loop09-alarm-crud-report.md` / QA `qa/reports/2026-07-02-loop09-alarm-crud-qa.md` (PASS).
  - **Loop 10 (완료, Phase 3)**: hana-event 평가엔진 — hana-common `AlarmEvent` 계약(10-A), yml alarm 토픽 `hdt-alarm-events`+producer(10-B), hana-event `alarm` 패키지 7파일(10-C: Reader/Cache/Evaluator/Publisher/Pipeline, 정책 R2DBC 로드·캐시·THRESHOLD/TOGGLE·cooldown/sustain·카테고리 소분류 전개). 기존 EventService 무수정(별도 병렬 파이프라인). QA `qa/reports/2026-07-03-loop10-alarm-engine-qa.md` (PASS).
    - MVP 한계(후속): 인메모리 상태 재기동 소실·다중 인스턴스 중복발행·리프레시 30초 지연·대량 device 캐시 메모리·실기동 통합테스트 필요.
  - **Loop 11 (완료, 아키텍처 정정)**: 알람 출력 경로 kafka→**websocket 통합**. 사용자 확정 정책 — event-server(hana-event)=kafka수신·history·현재값·**event판별→websocket** / digitaltwin=외부API·디바이스제어(후속)·**client API**(알람 이력 조회) / gateway=dbview조합→kafka publish.
    - 11-A(hana-event): AlarmPublisher(kafka) 삭제 → 평가결과를 EventHistoryDto(eventType="ALARM")로 기존 websocket sink emit + EventSnapshotService가 ALARM이면 tbl_alarm_history INSERT(R2DBC). FIRE_BELL 경로 무변경.
    - 11-B(digitaltwin): AlarmHistory 엔티티(tbl_alarm_history, ddl-auto) + client 조회 API `/api/alarm/history`. 컬럼 계약 21개 event-server INSERT와 1:1.
    - 11-C(devops): alarm kafka 토픽 yml 롤백. **Phase4(digitaltwin consumer)·alarm 토픽 폐기.**
    - QA `qa/reports/2026-07-03-loop11-alarm-websocket-qa.md` (PASS).
    - **후속 항목**: HdtKafkaTopicProperties.alarm 미사용 필드 정리(spring-backend). ddl-auto 기동순서 digitaltwin 선기동. tbl_alarm_history 인덱스 실DB 확인. **실기동 통합테스트 미실시(런타임 INSERT 미실증) → 재검증 권장.**
  - **남은 작업**: 프론트(정책 등록 UI + 알람 이력 화면 + websocket 알람 수신) · 후속(SMS·시나리오) · 레거시 event 도메인(EventRule) 정리 · 실기동 통합테스트.
  - **후속 항목(추적)**: ddl-auto만으로는 CHECK/UNIQUE/인덱스 미생성 → database-agent 명시 마이그레이션 권장. 레거시 EventRule + DeviceCategoryCascadeService 참조 정리(변경 요청서). 소프트 참조(category/device/sopTemplate id) 정합성 검증 위치 확정.
- **Loop D (예정, 미착수)**: ③ 서비스 재시작 — 인프라+보안 영역(윈도우: WinSW/NSSM 서비스 래퍼 + auto-restart + supervisor + 인증 게이트). 별도 승인 요청서로 진행.
- **다음 Loop 후보**는 각 QA 보고서 말미의 "다음 Loop 제안" 참조.

> 새 작업을 시작할 때 메인 Claude는 먼저 `.claude/skills/loop-coding/SKILL.md`의 "다음
> Loop 작업 계획" 양식을 출력하고, 코드 수정이 필요하면 승인을 요청한다.
