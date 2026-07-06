# AGENT 작업 보고서

## 1. 작업 AGENT
spring-backend-agent

## 2. 작업 일시
2026-07-01 (Loop 06-B)

## 3. 작업 목표
Loop 06-A에서 변경된 hana-common 모니터링 DTO(ResourceUsage에 diskUsage 추가,
MonitorSnapshot에서 host 제거·dependencies 추가, HostSummary 삭제, DependencyStatus 신규)에
맞춰 `MonitorService.java`를 정합화한다.
- HostSummary 기반 상단 호스트 요약 제거 → DependencyStatus 목록 조립으로 대체
- 서비스별 diskUsage 수집(각 서비스 health의 diskSpace 재사용)

## 4. 수정한 파일
- `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/monitor/service/MonitorService.java` (단일 파일)

## 5. 생성한 파일
- `docs/reports/2026-07-01-loop06B-monitorservice-report.md` (본 보고서)

## 6. 주요 작업 내용

### 제거된 것
- import `HostSummary` (→ `DependencyStatus`로 교체)
- 필드 `List<InetSocketAddress> networkTargets` → `List<DependencyTarget> dependencyTargets`
- 생성자 파라미터 `@Value("${hdt.monitor.network-targets:...}")` → `@Value("${hdt.monitor.dependencies:DB=192.168.250.181:5432,Kafka=192.168.250.249:9092}")`
- 메서드 `collectHostSummary(String)` — HostSummary 조립 로직 전체
- 메서드 `collectHeapUsage(String)` — 호스트 요약 전용(더 이상 미사용)
- 메서드 `collectDbStatus(Map)` — 호스트 요약 전용(더 이상 미사용)
- 메서드 `collectNetworkStatus()` — dependencies 조립으로 흡수
- 메서드 `fetchHealth(String)` — 각 서비스 health를 pollService가 이미 확보하므로 중복 제거
- 헬퍼 `taggedMetricValue(...)`, `valueAsMap(...)` — 위 제거로 orphan → 정리
- 메서드 `parseNetworkTargets(String)` → `parseDependencyTargets(String)`로 대체

### 추가된 것
- 메서드 `List<DependencyStatus> collectDependencies()`:
  - `dependencyTargets` 각 대상에 기존 `isReachable(InetSocketAddress)`(1초 TCP connect timeout) 적용
  - 도달 시 `LiveStatus.UP`, 실패/타임아웃 시 `LiveStatus.DOWN`
  - `DependencyStatus.builder().name(name).target(host+":"+port).status(...).build()`로 리스트 구성
  - 목록이 비면 빈 리스트 반환
- 메서드 `parseDependencyTargets(String)`:
  - 콤마 구분 `name=host:port` 토큰 파싱. 첫 `=` 기준으로 name 분리(host:port의 `:`과 혼동 방지)
  - `name=`이 없으면 host를 name으로 사용
  - 포트 파싱 실패/형식 오류 토큰은 warn 로그 후 무시
- record `DependencyTarget(String name, InetSocketAddress address)` + `displayTarget()`("host:port")
- `collectResource(...)` 시그니처에 `Map<String,Object> health` 추가 → 기존 `collectDiskUsage(health)`
  재사용해 `ResourceUsage.diskUsage` 채움
- `getSnapshot()`에서 host 대신 `collectDependencies()` 결과를 `MonitorSnapshot.dependencies`로 세팅

### diskUsage 수집 위치
- `pollService()`가 status 판정용으로 이미 가져온 `health` map을 `collectResource(serviceName, baseUrl, health)`로 전달
- `collectResource` 내부에서 기존 `collectDiskUsage(health)`(components.diskSpace.details.{total,free} → (total-free)/total, show-details:always 전제)로 계산
- health가 UP인 경로에서만 collectResource가 호출되므로 UP일 때만 수집. 컴포넌트/필드 부재·total<=0·범위이탈 시 센티넬 -1.0
- 별도 self 전용 host 요약은 제거되었고, self(digitaltwin)도 services 목록의 한 항목으로서 cpu/heap/diskUsage가 resource에 담김

## 7. 기술적 판단 근거
- 각 서비스의 diskUsage는 pollService가 확보한 health를 재사용하므로 추가 HTTP 호출 0회(승인 요청서의 "이미 가진 health map 재사용 권장" 준수)
- CPU/힙은 기존 collectResource가 processCpuLoad/systemCpuLoad/heapUsed/heapMax로 수집 중 — 그대로 유지, 추가 필드 없음
- 파싱은 `lastIndexOf(':')`로 host:port 분리하는 기존 스타일을 유지하되, name 접두 지원을 위해 첫 `=`만 name 경계로 사용
- 도달성/센티넬 방어 로직은 기존 isReachable / collectDiskUsage를 그대로 재사용
- 새 의존성 없음. spring-web 내장 RestClient / java.net.Socket만 사용

## 8. 다른 AGENT 영역 영향 여부
- devops-agent: 설정 키 신설 `hdt.monitor.dependencies`(기본값 코드 내장 `DB=192.168.250.181:5432,Kafka=192.168.250.249:9092`). yml override가 필요하면 devops-agent 승인 필요. 기존 `hdt.monitor.network-targets` 키는 코드에서 미사용 처리됨(yml에 남아 있어도 무해하나 정리 대상 → devops-agent 판단).
- react-frontend-agent(06-C): 응답 계약 변경 반영 필요.
  - 상단 카드: `dependencies[]`의 `name`/`target`/`status`(UP/DOWN)
  - 서비스별 컬럼: `services[].resource.diskUsage`, `resource.processCpuLoad`, `resource.heapUsedBytes`/`heapMaxBytes`
  - `host`(HostSummary) 필드는 응답에서 제거됨
- database-agent: 영향 없음

## 9. 남은 이슈
- yml에 잔존할 수 있는 `hdt.monitor.network-targets` 키 정리 여부(devops-agent 소관). 코드는 해당 키를 더 이상 읽지 않음.

## 10. QA 요청 사항
- `MonitorService.java`가 변경 DTO(diskUsage / dependencies)와 정합한지
- dependencies 파싱: `name=host:port` 및 `host:port`(name 생략) 두 케이스, 잘못된 포트 무시 동작
- diskUsage가 health UP일 때만 채워지고 실패/부재 시 -1.0 센티넬인지
- 제거된 심볼 잔존 참조 없음(HostSummary/networkTargets/collectNetworkStatus 등) — 확인 완료
- `./gradlew :hana-digitaltwin:compileJava -q` 컴파일 성공 — 확인 완료

## 11. 다음 Loop 제안
- Loop 06-C: 프론트 모니터링 화면을 dependencies 카드 + 서비스별 diskUsage/cpu/heap 컬럼으로 재구성(react-frontend-agent)
- devops: application.yml의 `hdt.monitor.network-targets` 제거 및 필요 시 `hdt.monitor.dependencies` 명시(승인 게이트)
