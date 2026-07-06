# QA 검증 보고서 — Loop 02-B Actuator/Management 설정

- 작성일: 2026-06-30
- 작성 AGENT: qa-code-reviewer
- 검증 대상 Loop: 02-B (devops-agent)
- 근거 문서
  - 승인 요청서: `qa/change-requests/2026-06-30-loop02-monitoring-actuator-aggregator.md`
  - 작업 보고서: `docs/reports/2026-06-30-loop02B-actuator-config-report.md`
- 검증 방식: 읽기/검증 전용. 어떤 코드·설정도 수정하지 않음.

---

## 1. 전체 판정

**PASS** (후속 항목 2건 기록 — 모두 본 Loop FAIL 사유 아님)

본 Loop 02-B 의 순수 변경분(actuator 의존성 3줄, management 노출 블록 3개, gateway
web-application-type 1줄)은 의도대로 정확히 반영되었고, 기존 설정값 훼손 없음,
YAML 파싱 정상, 3개 모듈 컴파일 성공.

---

## 2. 검증 대상 / 범위 분리

git diff 로 6개 파일의 실제 변경을 확인했고, 사용자/보고서가 명시한 "본 Loop 무관 사전
변경"을 검증 대상에서 제외했다.

- 제외(사전 `M`, 본 Loop 산출물 아님):
  - `hana-event/application.yml`: postgres host `.88 → .181`, kafka bootstrap 주석 토글
  - `hana-digitaltwin/application.yml`: `logging:` 블록, `app.building-lighting:` 블록
  - `hana-digitaltwin/build.gradle`: openapi3 / copyApiSpec / bootJar task 주석 처리
- 위 사전 변경은 정상 인지·기록되었으며, 본 판정에 반영하지 않음.

---

## 3. 항목별 검증 결과

### 3.1 의도한 변경만 정확히 반영 — PASS
build.gradle 3개 모두 `implementation project(":hana-common")` 바로 다음에 1줄만 추가:
```
implementation "org.springframework.boot:spring-boot-starter-actuator"
```
- 버전 미명시 → BOM 관리 (3.5 참조). 근거: 각 build.gradle 의 actuator 줄, diff 일치.
- exposure 최소 노출 `health,info,metrics` 확인 (민감 endpoint 비노출).

### 3.2 기존 설정값 훼손 여부 — PASS
- gateway: `server.port: 8080` 유지(L1-2), kafka bootstrap(L10) 등 미변경. 변경된 줄은
  `web-application-type` 1줄(주석만 부가)뿐.
- event: `server.port: 8081` 유지. management 블록만 파일 끝에 추가.
- digitaltwin: `server.port: 8084` 유지. management 블록만 파일 끝에 추가.
- 3.1/3.2 모두 git diff 상 actuator/management/web-type 외 라인 변동 없음(사전 변경 제외).

### 3.3 YAML 문법/들여쓰기 유효성 — PASS
PyYAML `safe_load_all` 파싱 결과 3개 파일 모두 정상, management 키 구조도 Spring Boot
규약과 일치:
- hana-event: `{endpoints:{web:{exposure:{include:'health,info,metrics'}}}}`
- hana-gateway: `{server:{port:8090}, endpoints:{web:{exposure:{include:'health,info,metrics'}}}}`
- hana-digitaltwin: `{endpoints:{web:{exposure:{include:'health,info,metrics'}}}}`
- 참고: event/digitaltwin yml 은 파일 끝 개행 없음(`No newline at end of file`). 파싱·동작에
  영향 없으나 스타일 관점 경미 사항. (FAIL 아님)

### 3.4 gateway 변경 정합성 + 포트 부수효과 — PASS (위험 MEDIUM 기록)
- `web-application-type: none → reactive` 전환 확인(L8). reactive 기동 시 메인 Netty
  서버(8080)가 함께 뜨고, actuator 는 `management.server.port: 8090` 전용 포트로 분리됨.
- 정합성: aggregator 폴링 대상 gateway=8090 과 management.server.port 일치. PASS.
- 부수효과(위험 MEDIUM, 운영 확인 필요):
  - gateway 가 8080(비즈니스 컨트롤러 없는 빈 라우트) + 8090(actuator) **2개 포트 동시 점유**.
    배포 환경에서 8080/8090 점유·방화벽·기존 8080 사용 프로세스와의 충돌 여부 확인 필요.
  - 기능 영향은 없으나 기동 방식 변경이므로 실기동 1회 검증 권장(본 Loop 코드 결함은 아님).

### 3.5 컴파일 검증 — PASS
`./gradlew :hana-event:compileJava :hana-gateway:compileJava :hana-digitaltwin:compileJava -q`
→ **EXIT 0 (성공)**. actuator 추가로 인한 컴파일 영향 없음.

### 3.6 BOM 버전 관리 정합성 — PASS
- `hana-common/build.gradle` L9: `mavenBom "org.springframework.boot:spring-boot-dependencies:3.4.1"`.
- 루트 `build.gradle` L4: `id "org.springframework.boot" ... apply false`.
- actuator 버전 미명시 → 3.4.1 BOM 으로 자동 정렬. 신규 외부 버전 충돌 없음. PASS.

### 3.7 aggregator 폴링 포트 정합성 — PASS
- event=8081(메인=actuator), gateway=8090(management 전용), digitaltwin=8084(메인=actuator).
- 실제 server.port(8081/8080/8084)·management.server.port(gateway 8090) 와 모두 일치.

---

## 4. 보고서 정확성 점검 (중요 정정 사항)

작업 보고서 4·7항 및 승인 요청서 8항은
"digitaltwin SecurityConfig 의 `/actuator/health`(또는 `/actuator/**`) permitAll 예외가
아직 없어 aggregator 폴링이 401 받을 수 있음 → Loop C 후속" 이라고 기술한다.

**실제 코드 확인 결과 이 전제는 이미 해소되어 있다.**
- `hana-digitaltwin/.../global/security/SecurityConfig.java` L54 에 `"/actuator/**"` 가
  `permitAll()` 목록(L52-67)에 **이미 포함**되어 있다.
- 해당 라인은 본 Loop 산출물이 아니라 기존 커밋 `6b8200f`([DIGITALTWIN] 서버 리팩토링
  1차 완료)에서 추가됨. SecurityConfig 는 워킹트리/스테이지 모두 미변경.

→ 결론: digitaltwin(8084) actuator 폴링은 Security 에 의해 401 차단되지 **않는다**.
보고서의 401 우려는 현재 코드 기준 과대평가이며, Loop C 에서 "추가"가 아니라 "기존 예외
유효성 재확인"으로 범위를 정정하는 것이 정확하다. (본 Loop 설정 결함 아님 → 판정 영향 없음)

---

## 5. 위험도 종합

**LOW ~ MEDIUM** (작업 보고서 평가와 일치)
- LOW: build.gradle 의존성 3줄, event/digitaltwin management 노출 — 기능 영향 없음, 컴파일 OK.
- MEDIUM: gateway `none → reactive` 전환에 따른 8080/8090 2포트 동시 점유. 운영 환경
  포트/방화벽 충돌 가능성은 실기동으로만 최종 확인 가능.

민감정보·의존성 위험: actuator 노출이 `health,info,metrics` 최소셋으로 제한되어 env/beans/
heapdump 등 민감 endpoint 비노출. 신규 외부 의존성 0. 양호.

---

## 6. 수정 요청 대상 AGENT

- 본 Loop 02-B 설정 변경에 대한 **코드 수정 요청 없음** (PASS).
- 문서 정정(권고, 선택): `docs-architect` 또는 `loop-orchestrator` — 작업/요청서의
  "actuator permitAll 미처리" 서술을 "기존 `/actuator/**` permitAll 존재, Loop C 는
  유효성 확인"으로 정정. 코드 영향 없으므로 강제 아님.

---

## 7. 재검증 필요 여부 / Loop C 진행 가능 여부

- **재검증 불필요.** 본 Loop 02-B 는 PASS.
- **Loop C 진행 가능.** 차단 사유 없음.

### 후속 항목 (Loop C 및 운영)
1. (Security) digitaltwin SecurityConfig `/actuator/**` permitAll 은 **이미 존재**(L54).
   Loop C 는 신규 추가가 아니라 actuator 도입 후 health 폴링이 실제 200 응답하는지 유효성만
   확인하면 됨. 필요 시 `/actuator/health` 로 매처 범위 축소(현재 `/actuator/**` 는 metrics·info
   까지 인증 없이 외부 노출되므로, 운영 네트워크 경계/포트 접근통제 정책과 정합 점검 권장).
2. (gateway 포트 운영) reactive 전환 후 8080(빈 라우트)+8090(actuator) 동반 기동을 실기동
   1회로 확인. 8080 미사용이면 추후 main 서버 비활성 또는 포트 통합 검토 가능(별도 Loop).
3. (스타일, 경미) event/digitaltwin yml 파일 끝 개행 누락 — 동작 영향 없음, 차후 정리 시 반영.

---

## 8. 검증 근거 요약 (파일·라인)

- `hana-event/build.gradle` L9 / `hana-gateway/build.gradle` L9 / `hana-digitaltwin/build.gradle` L11
  — actuator 의존성 1줄 추가(버전 미명시).
- `hana-gateway/src/main/resources/application.yml` L8(web-application-type reactive),
  L75-81(management.server.port 8090 + exposure).
- `hana-event/src/main/resources/application.yml` 말미 management 블록 + server.port 8081(L2).
- `hana-digitaltwin/src/main/resources/application.yml` 말미 management 블록 + server.port 8084(L2).
- `hana-common/build.gradle` L9 — BOM 3.4.1.
- `hana-digitaltwin/.../global/security/SecurityConfig.java` L54 — 기존 `/actuator/**` permitAll.
- 컴파일: `gradlew :hana-event:compileJava :hana-gateway:compileJava :hana-digitaltwin:compileJava -q` EXIT 0.
- YAML: PyYAML safe_load_all 3개 파일 파싱 성공.
