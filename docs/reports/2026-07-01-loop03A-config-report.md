# AGENT 작업 보고서

## 1. 작업 AGENT
devops-agent (빌드·설정·배포 계층)

## 2. 작업 일시
2026-07-01

## 3. 작업 목표
Loop 03-A: 모니터링 콘솔 필드 확장을 위한 설정 계층 준비.
- hana-common 에 공유 InfoContributor(PID 노출) 컴파일용 actuator 의존성(compileOnly) 추가
- hana-event / hana-gateway / hana-digitaltwin 3개 서비스의 actuator health 엔드포인트에
  `endpoint.health.show-details: always` 추가 (health 상세 컴포넌트 노출)

승인 근거: `qa/change-requests/2026-07-01-loop03-monitoring-console-fields.md` (사용자 명시 승인)

## 4. 수정한 파일
1. `d:\HDD\05-02.hanadream\hana-common\build.gradle`
   - dependencies 블록에 `compileOnly "org.springframework.boot:spring-boot-starter-actuator"` 1줄 + 주석 추가
2. `d:\HDD\05-02.hanadream\hana-event\src\main\resources\application.yml`
   - `management.endpoint.health.show-details: always` 추가
3. `d:\HDD\05-02.hanadream\hana-gateway\src\main\resources\application.yml`
   - `management.endpoint.health.show-details: always` 추가 (기존 `management.server.port: 8090`, `endpoints.web.exposure.include` 유지)
4. `d:\HDD\05-02.hanadream\hana-digitaltwin\src\main\resources\application.yml`
   - `management.endpoint.health.show-details: always` 추가

## 5. 생성한 파일
- `docs/reports/2026-07-01-loop03A-config-report.md` (본 보고서)

## 6. 주요 작업 내용
- build.gradle: 버전 미명시(BOM 3.4.1 관리), compileOnly 스코프로 추가하여 런타임 전이 없음.
  각 서비스는 이미 actuator 런타임 의존성을 보유하므로 hana-common 은 컴파일 전용으로만 참조.
- 3개 yml: management 블록에 `endpoint:`(단수) 키를 추가하고 그 하위에 `health.show-details: always` 배치.
  기존 `endpoints:`(복수)/`server:` 키는 그대로 유지. 들여쓰기 2-space YAML 규칙 준수.
- 승인 요청서에 명시된 4개 파일 외 어떤 파일도 수정하지 않음. 기존 설정값 변경 없이 추가만 수행.

## 7. 기술적 판단 근거
- compileOnly 선택: 런타임 actuator는 각 실행 모듈(event/gateway/digitaltwin)이 이미 제공.
  hana-common(java-library)은 공유 InfoContributor 컴파일에만 actuator API가 필요하므로
  런타임 전이를 유발하지 않는 compileOnly 가 BOM 일관성·의존성 최소화 원칙에 부합.
- show-details: always : health 세부 컴포넌트(db, diskSpace, ping 등)를 응답에 포함시켜
  03-C의 digitaltwin aggregator 가 컴포넌트별 상태를 파싱할 수 있게 함.

## 8. 검증 결과
- git diff 확인: 의도한 4개 파일의 4개 추가(actuator compileOnly 1건 + show-details 3건)만 존재.
  나머지 diff 라인은 본 작업 이전부터 워킹트리에 있던 Loop 02-B 미커밋 변경(host/kafka/
  web-application-type/management 블록 신설)으로, 본 작업에서 건드리지 않음.
- 컴파일 검증: `./gradlew :hana-common:compileJava -q` → EXIT=0 (성공).
  actuator compileOnly 추가가 hana-common 컴파일에 영향 없음 확인.

## 9. 설정 변경 요약 / 영향 모듈 / 빌드·배포 영향 / 위험도 / 롤백
- 설정 변경 요약: actuator compileOnly 1건(hana-common), health show-details:always 3건(event/gateway/digitaltwin).
- 영향 모듈: hana-common(컴파일), hana-event / hana-gateway / hana-digitaltwin(런타임 actuator 응답 형태 변경).
- 빌드 영향: hana-common 컴파일 정상. 다른 모듈 빌드 영향 없음(추가만).
- 배포 영향: 재기동 시 각 서비스의 `/actuator/health` 응답에 컴포넌트 상세(db/diskSpace 등) 포함됨.
  gateway 는 management 포트 8090 에서 노출.
- 위험도: MEDIUM.
  - `show-details: always` 는 인증/인가 조건 없이(현재 actuator 시큐리티 미적용 가정) health 응답에
    DataSource(db) 및 디스크(diskSpace) 상세를 노출한다. 무인증 노출 시 내부 인프라 상태가
    외부에 드러날 수 있으므로, 운영 배포 전 actuator 엔드포인트 접근 제어(네트워크/시큐리티) 확인 필요.
- 롤백 방안: 각 yml 의 `endpoint.health.show-details: always` 블록 3줄 제거,
  build.gradle 의 actuator compileOnly 1줄 제거. 다른 설정 변경 없어 단순 되돌리기로 원복 가능.

## 10. 다른 AGENT 영역 영향 여부
- 있음. 후속 Loop 의존:
  - 03-B (spring-backend-agent): hana-common 에 PidInfoContributor + DTO 확장 구현.
    본 Loop 에서 추가한 actuator compileOnly 의존성을 전제로 함.
  - 03-C (spring-backend-agent / digitaltwin): digitaltwin aggregator 가 health show-details 의
    컴포넌트(db / diskSpace)를 파싱하여 모니터링 콘솔 필드를 구성. 본 Loop 의 show-details:always 를 전제로 함.
- 위험 항목(재기재): `show-details: always` 가 db/diskSpace 상세를 무인증 노출하는 점 —
  03-B/03-C 진행 및 운영 배포 전 actuator 접근 제어 검토 필요.
- devops 영역 외 코드(.java)·DTO 는 본 Loop 에서 수정하지 않음(03-B 소관).

## 11. 남은 이슈
- actuator health 엔드포인트 접근 제어(시큐리티/네트워크) 정책 미정 — 운영 배포 전 결정 필요.
- 워킹트리에 Loop 02-B 미커밋 변경이 다수 존재(본 작업과 무관하나 커밋 시점에 주의).

## 12. QA 요청 사항
- qa-code-reviewer 에게 다음 검증 요청:
  1. 4개 파일에 의도한 추가 외 다른 설정값 변경이 없는지 (기존값 보존 확인).
  2. 3개 yml 의 `management.endpoint.health.show-details` 키 위치/들여쓰기 유효성(YAML 파싱).
  3. hana-common compileJava 성공 재확인.
  4. show-details:always 무인증 노출 위험도(MEDIUM) 판단 및 후속 조치 필요 여부.

## 13. 다음 Loop 제안
- 03-B: spring-backend-agent 가 hana-common 에 PidInfoContributor + 모니터링 DTO 확장 구현.
- 03-C: digitaltwin aggregator 의 health 컴포넌트 파싱 로직 구현.
- (병행 검토) actuator 엔드포인트 접근 제어 정책 확정 후 devops 반영.
