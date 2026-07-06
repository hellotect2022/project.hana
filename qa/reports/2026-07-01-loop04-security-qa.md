# QA 검증 보고서 — Loop 04 (A/B/C 통합)

- **작성일**: 2026-07-01
- **작성자**: qa-code-reviewer
- **대상 Loop**: 04-A(보안 규칙) + 04-B(actuator 관리 포트 바인딩) + 04-C(aggregator 폴링 포트)
- **근거 문서**:
  - 승인 요청서: `qa/change-requests/2026-07-01-loop04-monitor-admin-auth.md`
  - 작업 보고서: `docs/reports/2026-07-01-loop04A-security-report.md`, `-loop04B-actuator-bind-report.md`, `-loop04C-aggregator-port-report.md`
- **전체 판정**: **PASS**
- **재검증 필요 여부**: 불필요 (Loop 04 종료 가능)

---

## 1. 검증 범위 및 오탐 필터링

본 Loop 순수 산출물만 검증 대상으로 한정했다. 아래 미커밋 변경은 이전 Loop(02-B/03-A) 산출물로 판단하여 판정에서 제외했다(사전 지시대로 FAIL 사유 아님):

- gateway `web-application-type: none → reactive`
- 각 yml `management.endpoints/endpoint` 블록
- event `host`(192.168.250.88→181) / `kafka bootstrap-servers` 토글
- digitaltwin `logging.level.org.hibernate.*`, `app.building-lighting`

> 참고: 위 변경들이 동일 작업트리에 `management.server` 블록과 물리적으로 인접·혼재되어 커밋되어 있어, 커밋 단위에서 Loop 04 순수 변경분을 완전 격리하기 어렵다. 향후 Loop별 커밋 분리 권장(후속 항목 참조). 기능 검증에는 영향 없음.

---

## 2. 항목별 검증 결과

| # | 검증 항목 | 판정 | 근거 |
|---|---|---|---|
| 1 | 보안 규칙 순서(최중요) | PASS | `SecurityConfig.java:53` `/api/monitor/**` hasRole("ADMIN")가 `L54-69` `/api/**` permitAll **앞**에 선언. Spring Security는 첫 매칭 우선 → ADMIN 강제 성립 |
| 2 | 기존 규칙 미변경 | PASS | authenticated 목록(`L45-51`), actuator/swagger permitAll(`L54-69`), unlock ADMIN(`L70`), anyRequest authenticated(`L71`) 모두 유지. diff는 monitor 라인 2줄 추가뿐 |
| 3 | hasRole ADMIN 정합성 | PASS | `GrantedAuthorityDefaults("")`(`L33`) → ROLE_ 접두사 제거 → authority `"ADMIN"` 요구. `JwtAuthenticationFilter.java:65-68`이 `getRoles(token)`을 접두사 없이 `SimpleGrantedAuthority`로 매핑 → 토큰 role명 `ADMIN`과 일치. 기존 `/api/auth/unlock/**`(`L70`)와 동일 규약. 04-A 보고서 주장 타당 |
| 4 | 관리 포트 교차 정합성 | PASS | yml 관리 포트: gateway 8090, event 8091, digitaltwin 8085. `MonitorService.java:54-56` @Value 기본값: event `8091`, gateway `8090`, digitaltwin `8085` — **정확히 일치**. `hdt.monitor.*` yml override 없음(확인) → 기본값 유효 |
| 5 | 폴러 무토큰 정당성 | PASS | `MonitorService`는 `/actuator/health`,`/info`,`/metrics/*`만 폴링(`L100,133-134,202,333,347,354`). `/api/monitor` 재귀 호출 없음. self 폴링 대상 = `digitaltwinBaseUrl`(localhost:8085)의 actuator(`L83,225`). actuator는 permitAll(`L56`)+127.0.0.1 바인딩 → 토큰 미주입 타당 |
| 6 | localhost 바인딩 / 메인 포트 | PASS | `management.server.address: 127.0.0.1` 3개 yml 모두 존재. 메인 `server.port` 미변경(gateway 8080 / event 8081 / digitaltwin 8084). endpoints/endpoint 형제 키 병합 정상, 각 파일 최상위 `management:` 키 단 1개(중복 없음) |
| 7 | YAML 유효성 | PASS | `yaml.safe_load_all` 3개 파일 전부 OK |
| 8 | 범위 준수 | PASS(주의) | 04-A: `SecurityConfig.java` 1파일(tracked, M). 04-B: 3개 application.yml. 04-C: `monitor/` 신규 디렉토리(untracked). Loop 04 의도 파일 외 추가 침범 없음 |
| 9 | 컴파일 | PASS | `./gradlew :hana-digitaltwin:compileJava -q` → EXIT=0 |

---

## 3. 발견 사항 (Non-blocking)

- **(INFO) 스테일 주석**: `MonitorController.java:19-20` 에 "SecurityConfig는 수정하지 않는다"는 주석이 남아 있으나, 04-A에서 실제로 SecurityConfig를 수정했다. 기능 영향 없음, 문서/주석 정합성 차원의 후속 정리 권장(코드 수정은 아님).
- **(INFO) 커밋 격리**: 2번 항목 참조. Loop 04 변경분이 이전 Loop 미커밋 변경과 한 작업트리에 혼재. 커밋 시 Loop 단위 분리 권장.

---

## 4. Loop 04 종료 가능 여부

**종료 가능 (PASS).** 핵심 정합성(보안 규칙 순서, hasRole 규약, 관리 포트 3중 일치, 폴러 무토큰 근거, localhost 바인딩, 컴파일) 전부 충족. 코드/설정 수정 요청 없음(수정 대상 AGENT 없음).

---

## 5. 후속 항목 (운영 전제 및 다음 Loop 후보)

1. **동일호스트 전제**: 현 aggregator는 3개 서비스가 **동일 호스트**에서 기동(폴링 대상 localhost:8090/8091/8085)됨을 전제한다. 분산 배포 시 `management.server.address: 127.0.0.1`(외부 미노출)과 localhost 폴링이 충돌 → 분산 배포 재설계 필요(`hdt.monitor.targets.*` override + 관리 포트 바인딩 정책 동반 변경).
2. **프론트 ADMIN 가드**: `/api/monitor/status`가 백엔드에서 ADMIN 강제되므로, 프론트에서도 비-ADMIN 사용자에게 모니터 메뉴/호출을 숨기는 라우트 가드 추가 권장(react-frontend-agent).
3. **방화벽 정책 유지**: 8085/8091(및 8090) 관리 포트는 127.0.0.1 바인딩으로 1차 차단되나, 방화벽에서 외부 유입 차단을 이중으로 유지할 것(devops-agent 운영 체크리스트).
4. **주석/커밋 정리**: 3절 INFO 항목(스테일 주석, Loop 단위 커밋 분리).
