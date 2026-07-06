# 코드 수정 승인 요청서

- 작성일: 2026-07-01
- Loop: 03
- 작성: loop-orchestrator
- 관련: 서버 관리 콘솔 화면(상단 카드 + 실행 중인 서비스 테이블) 데이터 제공. Loop 02 aggregator 확장.

## 0. 결정 사항 (사용자 선택 반영)
- 모니터링 대상: **hana 3개 서비스**(hana-event/gateway/digitaltwin). 화면의 hdt-* 이름은 예시 → 프론트에서 표시명만 매핑.
- 호스트 지표: **Actuator로 되는 것만**(디스크 diskSpace, DB db/r2dbc health). OSHI 미도입.
- 판정 기준: DB=actuator db health, 네트워크=의존처(DB·Kafka) TCP 도달성.
- 재시작 버튼: 프론트에서 전부 비활성(백엔드 재시작 API 미노출, Loop D).

## 1. 수정 필요 여부
**필요.** PID 노출을 위한 공유 로직에 actuator 컴파일 의존성, DB/디스크 컴포넌트 상태 노출을 위한
health show-details 설정이 있어야 화면 항목을 채울 수 있다.

## 2. 문제 요약
Loop 02 aggregator(`GET /api/monitor/status`)는 상태·힙·CPU·스레드·uptime만 제공한다. 콘솔 화면은
추가로 PID, 시작시각, 프로세스 메모리(MB), 디스크%, DB 연결, 네트워크 도달성이 필요하다.

## 3. 화면 항목 → 데이터 소스 매핑
- 상태 → health LiveStatus (기존)
- PID → 커스텀 공유 InfoContributor(신규) via /actuator/info
- 시작시각 → `process.start.time` metric (Micrometer 기본, 추가코드 없음)
- 메모리(MB) → `jvm.memory.used` (기존, 포맷)
- CPU% 카드 → `system.cpu.usage` (기존)
- 메모리% 카드 → **JVM 힙%로 대체표기**(heapUsed/heapMax). 호스트 물리메모리 아님(OSHI 미도입).
- 디스크% 카드 → actuator `diskSpace` health 컴포넌트(total/free)
- DB 연결 카드 → actuator `db`(JPA)/`r2dbc`(WebFlux) health 컴포넌트
- 네트워크 카드 → aggregator에서 의존처 호스트:포트 TCP 도달성 체크

## 4. 수정 대상 파일
### (가) 승인 필수 — 수정 금지 파일
- `hana-common/build.gradle` — `compileOnly "org.springframework.boot:spring-boot-starter-actuator"` 1줄 (InfoContributor 컴파일용, 런타임은 각 서비스가 이미 보유)
- `hana-event/src/main/resources/application.yml` — `management.endpoint.health.show-details: always`
- `hana-gateway/src/main/resources/application.yml` — 동일
- `hana-digitaltwin/src/main/resources/application.yml` — 동일

### (나) 기존 파일 수정 (승인 범위)
- `hana-common/.../monitor/dto/ServiceMonitor.java` — 필드 추가: `Long pid`, `OffsetDateTime startedAt`, (선택) `LiveStatus dbStatus`
- `hana-common/.../monitor/dto/MonitorSnapshot.java` — `HostSummary host` 필드 추가

### (다) 신규 파일
- `hana-common/.../monitor/dto/HostSummary.java` — 상단 카드용 record(cpuUsage, memoryUsedPercent(힙), diskUsedPercent, LiveStatus networkStatus, LiveStatus dbStatus)
- `hana-common/.../monitor/info/PidInfoContributor.java` + auto-config 등록(META-INF AutoConfiguration.imports) — 각 서비스 /actuator/info에 pid 노출
- `hana-digitaltwin/.../monitor/` aggregator 확장(신규 파일 또는 기존 MonitorService 확장)

## 5. 수정 사유
콘솔 화면 요구 필드를 표준 Actuator 자산으로 충족. hana-common 공유로 3개 서비스가 동일 PID 노출
로직을 재사용(사용자 요청 구조). OSHI 없이 가능한 범위로 한정.

## 6. 수정 방향
1. hana-common build.gradle actuator compileOnly 추가 → PidInfoContributor + auto-config 작성.
2. yml ×3 show-details: always → aggregator가 health 컴포넌트(db/diskSpace) 파싱 가능.
3. DTO 확장(ServiceMonitor/MonitorSnapshot) + HostSummary 신규.
4. digitaltwin MonitorService 확장: /actuator/info(pid), metrics(start.time, memory), health components(db/disk) 파싱, 의존처 도달성 체크로 HostSummary 조립.

## 7. 예상 변경 내용
- 추가: build.gradle 1줄, yml show-details ×3, DTO 필드/신규 DTO, InfoContributor+auto-config, aggregator 로직.
- 변경: ServiceMonitor/MonitorSnapshot 필드 확장(하위호환 — 필드 추가만).
- 삭제: 없음.

## 8. 위험도
**LOW~MEDIUM**
- 대부분 추가/확장. DTO 필드 추가는 하위호환.
- MEDIUM 요인: show-details: always 가 db/disk 상세를 무인증 노출(현 /api/** 개방 posture와 동일 선상). 운영 접근통제 후속항목.

## 9. 수정하지 않을 경우 문제
- 콘솔 화면의 PID/시작시각/디스크/DB/네트워크 칸을 채울 수 없어 상태 테이블만 표시 가능.

## 10. 승인 요청
사용자 승인 완료(2026-07-01). 진행 순서:
- **03-A (devops)**: hana-common build.gradle + yml ×3 (본 요청서 대상)
- **03-B (spring-backend)**: hana-common DTO 확장 + PidInfoContributor + auto-config
- **03-C (spring-backend)**: digitaltwin aggregator 확장
- 각 Loop 후 작업 보고서 + qa-code-reviewer 검증.
