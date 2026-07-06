# 코드 수정 승인 요청서

- 작성일: 2026-07-01
- Loop: 06
- 작성: loop-orchestrator
- 관련: 모니터링 화면 서비스별 관점 재설계 (분산 배포 대비)

## 0. 배경 / 결정 (사용자 승인)
현재 상단 카드(CPU/힙/디스크)는 digitaltwin(self) 한 대 기준이라 서버가 분산되면 오해를 준다.
- **서비스별 리소스 + 클릭 상세**로 재설계.
- **상단 카드 = 인프라 의존성(DB 서버·Kafka 서버 도달성 UP/DOWN)** 으로 재편.
- **DB/Kafka는 도달성(UP/DOWN)만** (actuator 없음 → CPU/디스크 미수집, exporter 도입은 후속 별도 검토).

## 1. 설계 개요
- 각 Spring 서비스(event/gateway/digitaltwin): 자기 CPU%·힙%·디스크%·스레드·uptime·응답시간 표시.
  - CPU% = `processCpuLoad`, 힙% = heapUsed/heapMax(프론트 계산), 디스크% = **신규 서비스별 수집**.
- 인프라(DB/Kafka): 이름+host:port 도달성(UP/DOWN) 목록 → 상단 카드.

## 2. 수정 대상 파일
### 06-A (spring-backend, hana-common)
- `ResourceUsage.java` (수정) — 필드 추가 `double diskUsage` (0~1, -1 센티넬; 해당 서비스 호스트 디스크).
- `DependencyStatus.java` (신규 record) — `String name, String target, LiveStatus status`.
- `MonitorSnapshot.java` (수정) — `HostSummary host` 제거, `List<DependencyStatus> dependencies` 추가.
- `HostSummary.java` (삭제) — 서비스별 관점 전환으로 obsolete.

### 06-B (spring-backend, digitaltwin)
- `MonitorService.java` (수정)
  - pollService에서 각 서비스 health의 diskSpace로 **서비스별 diskUsage** 계산해 ResourceUsage에 채움.
  - HostSummary 조립 제거 → `dependencies` 목록 조립: `hdt.monitor.dependencies`(name=host:port, 기본 `DB=192.168.250.181:5432,Kafka=192.168.250.249:9092`) 각 TCP 도달성.

### 06-C (react-frontend, 05-06/src)
- `SystemServerPage.tsx` (수정) — 상단 카드=dependencies(UP/DOWN Badge), 테이블에 CPU%/힙%/디스크% 컬럼, 서비스명 클릭 시 상세 패널(스레드/uptime/응답시간).
- `monitorService.ts` (수정) — 타입 주석(dependencies, diskUsage) 갱신.

## 3. 위험도
MEDIUM
- DTO 변경(MonitorSnapshot host→dependencies, ResourceUsage +diskUsage)은 프론트와 동시 변경 필요(계약 동기화). 06-A 후 digitaltwin/프론트 미갱신 상태에선 컴파일/렌더 깨짐 → 06-A→B→C 순차 후 통합 QA.
- 디스크는 서비스가 뜬 호스트 기준(공존 시 동일값). DB/Kafka는 도달성만(리소스 미수집).

## 4. 승인
사용자 승인 완료(2026-07-01, 서비스별+클릭상세 / DB·Kafka 도달성만). 06-A→B→C 순차, 각 후 보고서, 통합 QA.
