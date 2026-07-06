# AGENT 작업 보고서

## 1. 작업 AGENT
spring-backend-agent

## 2. 작업 일시
2026-06-30 (Loop 02 - A)

## 3. 작업 목표
livecheck + 리소스 모니터링 aggregator의 공통 응답 계약을 hana-common에 정의한다.
digitaltwin aggregator가 3개 서비스(hana-event/hana-gateway/hana-digitaltwin)의
actuator를 폴링해 이 DTO로 정규화하여 반환할 수 있도록 JVM/프로세스 레벨 리소스 지표
DTO를 신규 생성한다.

승인 근거: `qa/change-requests/2026-06-30-loop02-monitoring-actuator-aggregator.md`
(사용자 "승인" 완료).

## 4. 수정한 파일
없음 (기존 파일 무수정. 신규 파일만 생성).

## 5. 생성한 파일
패키지: `com.mpole.hana.common.monitor.dto`

- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/LiveStatus.java`
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/ResourceUsage.java`
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/ServiceMonitor.java`
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/MonitorSnapshot.java`

## 6. 주요 작업 내용
- `LiveStatus` (enum): 서비스 생존 상태 UP / DOWN / UNKNOWN.
- `ResourceUsage` (record + @Builder): JVM/프로세스 레벨 리소스 지표.
  - heapUsedBytes(long), heapMaxBytes(long), processCpuLoad(double, -1.0=측정불가),
    systemCpuLoad(double, -1.0=측정불가), threadCount(int), uptimeMs(long).
- `ServiceMonitor` (record + @Builder): 단일 서비스 모니터링 결과.
  - service(String), status(LiveStatus), responseTimeMs(Long, 미응답 null),
    resource(ResourceUsage, 수집 실패 시 null), detail(String, optional).
- `MonitorSnapshot` (record + @Builder): 여러 서비스 집계 스냅샷.
  - timestamp(OffsetDateTime), services(List<ServiceMonitor>).

## 7. 기술적 판단 근거
- 기존 hana-common DTO(`DeviceRawPointData`, `DeviceBundle`)가 Java `record` +
  lombok `@Builder` 조합을 사용하므로 동일 컨벤션을 그대로 적용했다.
- `MonitorSnapshot`의 `timestamp` 타입은 기존 `DeviceBundle`이 `OffsetDateTime`을
  쓰는 것과 동일하게 `java.time.OffsetDateTime`을 사용해 일관성을 유지했다.
- 측정 불가 값 표현은 null 대신 `-1.0` 센티넬을 사용(원시 double 필드). 이는 actuator
  metric 미지원 환경(`processCpuLoad`/`systemCpuLoad` 미수집)을 명시적으로 구분하기
  위함이며 승인 요청서 계약을 그대로 따른 것이다.
- `responseTimeMs`, `resource`는 미응답/수집 실패 시 null 허용을 위해 박스 타입/객체
  타입으로 선언했다.
- 외부 의존성 추가 없음. 모두 JDK 표준 타입 + 기존 lombok만 사용.

## 8. 다른 AGENT 영역 영향 여부
있음 (후속 Loop에서 소비).
- Loop B (devops-agent): hana-common을 의존하는 모듈에 spring-boot-actuator 추가 및
  `application*.yml` 노출 엔드포인트 설정 (build.gradle / yml 수정 필요).
- Loop C (spring-backend-agent, digitaltwin): digitaltwin aggregator가 본 DTO를
  소비하여 3개 서비스 actuator 폴링 결과를 정규화.
- 이번 Loop(A) 자체는 hana-common 신규 파일만 추가하므로 기존 모듈 컴파일/동작에 영향
  없음. 빌드 파일·yml·DB 변경 없음.

## 9. 남은 이슈
- 컴파일 검증은 본 Loop에서 미실시 (지시에 따라 Loop B에서 actuator 추가 후 일괄 빌드).
- aggregator 구현 시 actuator metric 키 매핑(`jvm.memory.used`, `process.cpu.usage`,
  `system.cpu.usage`, `jvm.threads.live`, `process.uptime` 등)은 Loop C에서 결정.

## 10. QA 요청 사항
- 신규 4개 파일이 기존 hana-common DTO 컨벤션(record + @Builder, 패키지 선언, import
  정리)을 준수하는지 검토.
- 필드 타입/null 허용 정책이 승인 요청서 계약과 일치하는지 확인.
- 기존 파일 무수정 / 빌드 파일 무수정 여부 확인.

## 11. 다음 Loop 제안
- Loop B (devops-agent): 대상 모듈 build.gradle에 spring-boot-actuator 의존성 추가,
  application.yml health/metrics 엔드포인트 노출 설정 (승인 게이트).
- Loop C (spring-backend-agent): digitaltwin에 aggregator 서비스/컨트롤러 구현,
  본 DTO로 정규화 후 REST 응답 제공.
