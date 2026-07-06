# 코드 수정 승인 요청서

- 작성일: 2026-06-30
- Loop: 02
- 작성: loop-orchestrator
- 관련 계획: 3개 서비스 livecheck + 리소스 모니터링 (재시작은 별도 Loop로 분리)

## 0. 결정 사항 (사용자 선택 반영)
- 리소스 수집 깊이: **JVM/프로세스 레벨만** (JDK `OperatingSystemMXBean`/Micrometer 기본, **새 외부 의존성 없음**)
- 재시작(③): **이번 범위 제외**, 별도 Loop D(WinSW + supervisor + 보안)로 미룸
- 노출 방식: **순수 Spring Boot Actuator + hana-digitaltwin aggregator**

## 1. 수정 필요 여부
**필요.** Actuator 엔드포인트는 의존성과 노출 설정이 있어야 동작하므로 `build.gradle`과
`application.yml` 수정이 불가피하다. (둘 다 수정 금지 파일 → 승인 필수)

## 2. 문제 요약
3개 서비스(hana-event / hana-gateway / hana-digitaltwin)에 대해 외부에서 살아있는지
(livecheck)와 리소스 상태(힙·CPU·스레드)를 조회할 표준 수단이 현재 없다.
- 현재 어떤 모듈에도 `spring-boot-starter-actuator` 의존성 없음.
- 3개 모듈 `application.yml` 모두 actuator/management 노출 설정 없음.

## 3. 영향 범위
- hana-common: **신규 파일만 추가** (기존 파일 수정 없음) — aggregator 응답 DTO 계약.
- hana-event / hana-gateway / hana-digitaltwin: `build.gradle` 의존성 1줄 + `application.yml`
  management 노출 블록 추가. 기존 라우팅·도메인 로직에는 영향 없음.
- hana-digitaltwin: aggregator용 **신규 파일** (Controller/Service) 추가. 기존 코드 수정 없음.

## 4. 수정 대상 파일
### (가) 승인 필수 — 수정 금지 파일
- `hana-event/build.gradle` — actuator 의존성 추가
- `hana-gateway/build.gradle` — actuator 의존성 추가
- `hana-digitaltwin/build.gradle` — actuator 의존성 추가
- `hana-event/src/main/resources/application.yml` — management 노출 설정
- `hana-gateway/src/main/resources/application.yml` — management 노출 설정
- `hana-digitaltwin/src/main/resources/application.yml` — management 노출 설정

### (나) 신규 파일 (기존 수정 아님)
- `hana-common/src/main/java/com/mpole/hana/common/monitor/dto/*.java`
  - 예: `ServiceStatus`(서비스명·UP/DOWN·응답시간), `ResourceUsage`(heapUsed/Max·cpuLoad·threads),
    `MonitorSnapshot`(여러 서비스 집계)
- `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/monitor/`
  - aggregator Controller + Service (각 서비스 actuator를 `RestClient`로 폴링·집계)

## 5. 수정 사유
- Actuator는 WebMVC(digitaltwin) / WebFlux(event·gateway) **양쪽 스택에서 동일하게 동작**,
  health·metrics를 표준 제공 → 직접 구현 대비 중복·유지비 최소.
- hana-common에 **응답 계약(DTO)만** 두면 3개 인스턴스가 동일 라이브러리를 공유(사용자 요청 구조).
- 리소스는 JVM 레벨이라 JDK/Micrometer 기본으로 충분 → **새 외부 의존성 0**.

## 6. 수정 방향
1. 3개 모듈에 `org.springframework.boot:spring-boot-starter-actuator` 추가(버전은 BOM 관리).
2. 각 `application.yml`에 `health`, `metrics`, `info` 정도만 노출 (최소 노출 원칙).
3. hana-common에 aggregator 응답 DTO 신규 추가.
4. digitaltwin에 aggregator API 신규 추가 — `RestClient`(spring-web 내장, 새 의존성 불필요)로
   event·gateway·self actuator를 폴링해 `MonitorSnapshot`으로 합쳐 1개 API로 반환.

## 7. 예상 변경 내용
- 추가: 각 `build.gradle` actuator 의존성 1줄(×3), 각 `application.yml` management 블록(×3),
  hana-common DTO 신규(수 개), digitaltwin aggregator 신규(Controller/Service).
- 변경: 없음(기존 코드 로직 변경 없음).
- 삭제: 없음.

## 8. 위험도
**LOW**
- 신규 추가 위주, 기존 도메인 로직 무변경.
- 유의점: actuator 엔드포인트는 **보안 노출 범위**를 최소화해야 함(민감 endpoint 비노출,
  인증 적용 여부는 Loop 진행 중 확인). HIGH 위험 요소인 재시작은 이번 범위에서 제외됨.

## 9. 수정하지 않을 경우 문제
- 외부에서 3개 서비스의 생존/리소스 상태를 표준적으로 확인할 방법이 계속 부재.
- 각 서비스에 비표준 커스텀 엔드포인트를 따로 구현하게 되어 MVC/WebFlux 중복 구현·유지비 증가.

## 10. 승인 요청
다음 순서로 진행하고자 함. **승인 / 보류 / 방향 변경** 중 선택 요청:
- **Loop A** (hana-common DTO 신규) — spring-backend-agent
- **Loop B** (3개 build.gradle + yml, 본 요청서 대상) — devops-agent, 승인 필수
- **Loop C** (digitaltwin aggregator 신규) — spring-backend-agent
- 각 Loop 후 작업 보고서 + qa-code-reviewer 검증.
- **Loop D(재시작)** 는 본 건 완료 후 별도 요청서로 분리.
