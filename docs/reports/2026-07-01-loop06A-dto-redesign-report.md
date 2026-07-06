# AGENT 작업 보고서

## 1. 작업 AGENT
spring-backend-agent

## 2. 작업 일시
2026-07-01

## 3. 작업 목표
모니터링을 "단일 호스트 요약" 관점에서 "서비스별 관점"으로 재설계하기 위한
hana-common 모니터링 DTO 계층 변경. 승인 요청서:
`qa/change-requests/2026-07-01-loop06-per-service-monitoring.md`.
범위는 `hana-common/src` 하위로 엄격히 한정.

## 4. 수정한 파일
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/ResourceUsage.java`
  - 기존 필드(heapUsedBytes, heapMaxBytes, processCpuLoad, systemCpuLoad, threadCount, uptimeMs) 뒤에
    `double diskUsage` 추가. 서비스가 뜬 호스트의 디스크 사용률(0.0~1.0, 측정 불가 -1.0).
  - record + `@Builder` 컨벤션 유지.
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/MonitorSnapshot.java`
  - `HostSummary host` 필드 제거.
  - `java.util.List<DependencyStatus> dependencies` 필드 추가.
  - 기존 `timestamp`, `services` 및 `@Builder` 유지.

## 5. 생성한 파일
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/DependencyStatus.java`
  - record + `@Builder`.
  - 필드: `String name`(표시명, 예 DB/Kafka), `String target`(host:port),
    `LiveStatus status`(UP/DOWN/UNKNOWN).
  - actuator 미노출 외부 인프라의 TCP 도달성 상태를 표현.

## 6. 주요 작업 내용
- 단일 호스트 요약 카드(HostSummary)를 폐기하고, 상단 카드를 인프라 의존처 도달성
  목록(dependencies)으로 대체.
- 디스크 사용률을 호스트 단일값이 아닌 서비스별 리소스(ResourceUsage) 지표로 이동하여
  서비스별 관점으로 일관화.
- `HostSummary.java` 삭제. 삭제 전 hana-common 내부 참조는 HostSummary.java 본체와
  MonitorSnapshot.java 뿐임을 grep 으로 확인했고, MonitorSnapshot 에서 참조 제거 후
  hana-common 내 HostSummary 잔존 참조 0건 확인.

## 7. 기술적 판단 근거
- diskUsage 를 ResourceUsage 에 둔 이유: 다중 서비스가 서로 다른 호스트에 뜰 수 있어
  디스크 사용률을 단일 호스트 요약으로 표현하면 왜곡됨. 서비스별 리소스에 귀속하는 것이
  서비스별 관점 재설계 취지에 부합.
- diskUsage 를 기존 필드 뒤에 추가: record 컴포넌트 순서 변경 시 하위 호환 및 빌더
  가독성 영향. 승인 요청서 명세대로 마지막에 배치.
- DependencyStatus 를 별도 record 로 분리: DB/Kafka 등 actuator 없는 외부 인프라는
  health 엔드포인트가 아닌 TCP 도달성으로만 판정 가능하여, 서비스 생존 지표
  (ServiceMonitor)와 의미가 다름. 별도 타입으로 분리하는 것이 명확.
- 컴파일 검증: `./gradlew :hana-common:compileJava -q` 실행, 종료코드 0(성공).
  digitaltwin 컴파일은 06-B 이전이라 의도적으로 수행하지 않음.

## 8. 다른 AGENT 영역 영향 여부
있음. 변경 요청 필요.
- **Loop 06-B (spring-backend-agent, digitaltwin)**: digitaltwin MonitorService 가
  HostSummary 를 사용하므로 이 변경으로 컴파일이 깨지는 것이 정상. 06-B 에서
  (a) HostSummary 참조 제거, (b) `dependencies`(DB/Kafka 등 TCP 도달성) 조립,
  (c) 서비스별 `diskUsage` 수집을 ResourceUsage 에 채우도록 수정 필요.
- **Loop 06-C (react-frontend-agent)**: 상단 카드 = `dependencies` 목록(name/target/status)
  으로 재구성, 서비스 테이블에 CPU/힙/디스크(ResourceUsage.diskUsage) 컬럼 표시로 수정 필요.
  기존 host 요약 카드 바인딩 제거.

## 9. 남은 이슈
- 이번 Loop 범위(hana-common) 내 이슈 없음.
- digitaltwin 은 06-B 수정 전까지 컴파일 불가 상태(의도된 상태).

## 10. QA 요청 사항
- hana-common 범위 준수 여부(다른 모듈/build.gradle/yml 미변경) 확인.
- record + `@Builder` 컨벤션 유지 여부 확인.
- HostSummary 삭제 후 hana-common 내 잔존 참조 0건 확인.
- `:hana-common:compileJava` 성공 재현.
- ResourceUsage.diskUsage 가 기존 필드 뒤에 배치되었는지 확인.

## 11. 다음 Loop 제안
- **Loop 06-B**: digitaltwin MonitorService 재작성(HostSummary 제거, dependencies 조립,
  서비스별 diskUsage 수집). 컴파일 복구.
- **Loop 06-C**: 프론트 모니터링 화면을 dependencies 카드 + 서비스별 리소스 테이블로 재구성.
