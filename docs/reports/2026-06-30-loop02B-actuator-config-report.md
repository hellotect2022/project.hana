# Loop 02-B 작업 보고서 — Actuator/Management 설정

- 작성일: 2026-06-30
- 작성 AGENT: devops-agent
- 승인 요청서: `qa/change-requests/2026-06-30-loop02-monitoring-actuator-aggregator.md`
- 승인 상태: 사용자 명시 승인 완료 (build.gradle/application.yml 수정, gateway reactive 동반 기동, management 포트 8090)

## 1. 작업 목표

모니터링 aggregator 가 폴링할 수 있도록 3개 백엔드 모듈(hana-event, hana-gateway,
hana-digitaltwin)에 Spring Boot Actuator 를 도입하고 health/info/metrics 엔드포인트를
노출한다. gateway 는 비즈니스 웹 서버 없이 기동(`web-application-type: none`)하던 것을
`reactive` 로 전환하여 전용 management 포트(8090)로 actuator 를 서빙한다.

## 2. 설정 변경 요약 (수정 6개 파일, 의도 변경만)

| 파일 | 변경 내용 |
|---|---|
| `hana-event/build.gradle` | `spring-boot-starter-actuator` 의존성 1줄 추가 (BOM 버전 관리, 버전 미명시) |
| `hana-gateway/build.gradle` | `spring-boot-starter-actuator` 의존성 1줄 추가 |
| `hana-digitaltwin/build.gradle` | `spring-boot-starter-actuator` 의존성 1줄 추가 |
| `hana-event/src/main/resources/application.yml` | 파일 끝에 `management.endpoints.web.exposure.include: health,info,metrics` 블록 추가 |
| `hana-digitaltwin/src/main/resources/application.yml` | 동일 management 블록 추가 |
| `hana-gateway/src/main/resources/application.yml` | (a) `spring.main.web-application-type: none → reactive` 변경, (b) `management.server.port: 8090` + exposure 블록 추가 |

기존 설정값(DB, kafka, server.port 등)은 변경하지 않음. 의존성 순서/내용도 추가 1줄 외 변경 없음.

### actuator 노출 엔드포인트 (모듈별)
- hana-event: `http://<host>:8081/actuator/health|info|metrics` (메인 서버 포트와 동일)
- hana-digitaltwin: `http://<host>:8084/actuator/health|info|metrics` (메인 서버 포트와 동일)
- hana-gateway: `http://<host>:8090/actuator/health|info|metrics` (전용 management 포트, 메인 8080은 비즈니스 컨트롤러 없이 동반 기동)

## 3. 영향 모듈

- hana-event (WebFlux/R2DBC) — actuator 추가, 동작 변화 없음, 8081 에 actuator 추가 노출.
- hana-digitaltwin (WebMVC/JPA) — actuator 추가, 8084 에 actuator 추가 노출. (보안 예외 미처리, 4항 참조)
- hana-gateway (WebFlux) — `none → reactive` 전환으로 Netty 메인 서버(8080)가 함께 기동.
  비즈니스 컨트롤러가 없으므로 8080 은 라우트 없이 떠 있고, actuator 는 8090 으로 분리.

## 4. 빌드/배포 영향

- 빌드 검증: `./gradlew :hana-event:compileJava :hana-gateway:compileJava :hana-digitaltwin:compileJava -q` → **컴파일 성공**.
  - 기존 `KafkaConfig.java` unchecked 연산 Note 1건은 본 변경과 무관한 사전 경고.
- 의존성: `spring-boot-starter-actuator` 는 Spring Boot 3.4.1 BOM 으로 버전 자동 정렬됨. 신규 외부 버전 충돌 없음.
- 배포 영향:
  - gateway 가 reactive 로 기동하면서 **8080 포트와 8090 포트를 동시 점유**한다. 배포 환경에서
    8080/8090 포트 충돌·방화벽 오픈 여부 확인 필요.
  - aggregator 폴링 대상 URL: event=8081, gateway=8090, digitaltwin=8084 (`/actuator/...`).

## 5. 위험도

**LOW ~ MEDIUM**

- LOW: build.gradle 의존성 추가, event/digitaltwin yml 노출 설정 — 기능 영향 없음.
- MEDIUM: gateway `web-application-type` 변경 — 기동 방식이 바뀐다(메인 Netty 서버 동반 기동, 8080/8090 두 포트 점유). 포트 충돌 가능성 및 reactive 컨텍스트 초기화 부작용 점검 필요.
- 보안: digitaltwin 은 Spring Security 적용 모듈로, `/actuator/**` 가 현재 SecurityConfig 기준
  인증 요구 대상일 수 있어 aggregator health 폴링이 401 을 받을 수 있음(Loop C 후속).

## 6. 롤백 방안

- 모든 변경은 추가/한 줄 치환 형태이므로 git 으로 즉시 복원 가능:
  - `git checkout -- hana-event/build.gradle hana-gateway/build.gradle hana-digitaltwin/build.gradle`
    하면 해당 파일 전체가 직전 워킹트리 상태로 복원되므로, **build.gradle 들은 본 변경(actuator 1줄)만
    선별 롤백** 하려면 해당 줄만 수동 제거 권장(특히 hana-digitaltwin/build.gradle 은 본 Loop 와 무관한
    사전 수정도 포함되어 있어 전체 checkout 시 그 변경까지 되돌아감 — 8항 참조).
  - application.yml 3개: 추가한 `management:` 블록(및 gateway 의 `web-application-type` 한 줄)만 제거.
- 부분 롤백 옵션:
  - actuator 만 비활성화: 각 yml 의 `management.endpoints.web.exposure.include` 를 제거하거나 빈 값으로.
  - gateway 만 원복: `web-application-type: reactive → none` 으로 되돌리면 8080/8090 미점유.

## 7. 다른 AGENT 영역 영향

- **Loop C (spring-backend-agent / digitaltwin SecurityConfig)**: digitaltwin SecurityConfig 에
  `/actuator/health` (필요 시 `/actuator/**`) permitAll 예외 추가 필요. **본 Loop 에서 미처리**.
  미처리 상태에서는 aggregator 가 digitaltwin(8084) health 폴링 시 인증 차단(401)될 수 있음.
- aggregator 는 event=8081 / gateway=8090 / digitaltwin=8084 의 actuator 를 폴링한다.
- event/gateway 는 Security 미적용(WebFlux 기본)으로 별도 예외 불필요.

## 8. 참고 — 본 Loop 와 무관한 사전 워킹트리 변경 (미수정)

작업 시작 시점 git status 기준, 다음 파일들은 본 Loop 02-B 이전부터 이미 수정(`M`)되어 있었으며
**본 작업에서 건드리지 않았다**. git diff 에 함께 보이지만 본 Loop 변경이 아님:

- `hana-event/src/main/resources/application.yml`: postgres host(192.168.250.88 → .181) 및
  kafka bootstrap-servers 주석 토글 (사전 변경).
- `hana-digitaltwin/src/main/resources/application.yml`: `logging:` / `building-lighting:` 블록 (사전 변경).
- `hana-digitaltwin/build.gradle`: openapi3/copyApiSpec task 주석 처리 (사전 변경).

본 Loop 의 순수 변경분은 각 파일의 actuator 의존성 1줄, management 블록, gateway
web-application-type 한 줄뿐이다.

## 9. QA 요청

qa-code-reviewer 에게 본 변경에 대한 검증을 요청한다. 중점 점검:
1. 6개 파일 외 변경 없음 / 기존 설정값 보존 여부.
2. actuator 의존성 BOM 버전 정렬(3.4.1) 일관성.
3. gateway reactive 전환에 따른 포트(8080/8090) 점유 및 부작용.
4. digitaltwin SecurityConfig actuator 예외 미처리(Loop C) 위험 기록 적정성.
