# 코드 수정 승인 요청서

- 작성일: 2026-07-01
- Loop: 04
- 작성: loop-orchestrator
- 관련: 모니터링 보안 강화 — /api/monitor/status admin JWT 보호 + raw actuator localhost 차단

## 0. 결정 사항 (사용자 승인)
- 방식: **통합 API만 admin JWT** + raw actuator는 **localhost 바인딩으로 외부 차단**(JWT 미부착 → aggregator 폴링 유지).
- 범위: 전부(04-A+B+C) 진행.
- 이전 "무인증 공개" 결정 번복. memory 갱신.

## 1. 수정 필요 여부
**필요.** SecurityConfig에서 /api/monitor/** 인가 규칙, 3개 yml에서 actuator localhost 바인딩,
aggregator 폴링 포트 갱신이 있어야 요구 보안이 성립.

## 2. 문제 요약
현재 /api/monitor/status 및 3개 서비스 actuator가 무인증 공개. 사용자는 admin 토큰이 있어야
모니터링 정보가 나오도록 원함. event·gateway는 Spring Security가 없어 actuator에 JWT를
직접 걸기 어렵고, actuator에 JWT를 걸면 aggregator 서버간 폴링이 401로 깨짐 → localhost 차단 채택.

## 3. 수정 대상 파일
### 04-A (spring-backend, 기존 파일 수정)
- `hana-digitaltwin/.../global/security/SecurityConfig.java`
  - `.requestMatchers("/api/monitor/**").hasRole("ADMIN")` 를 `/api/**` permitAll 규칙 **앞에** 추가(첫 매칭 우선).

### 04-B (devops, 수정 금지 파일 — 승인 필수)
- `hana-gateway/src/main/resources/application.yml` — `management.server.address: 127.0.0.1` 추가(기존 port 8090 유지).
- `hana-event/src/main/resources/application.yml` — `management.server.port: 8091` + `management.server.address: 127.0.0.1` 추가(현재 8081 공유 → 분리).
- `hana-digitaltwin/src/main/resources/application.yml` — `management.server.port: 8085` + `management.server.address: 127.0.0.1` 추가(현재 8084 공유 → 분리).

### 04-C (spring-backend, 기존 파일 수정)
- `hana-digitaltwin/.../monitor/service/MonitorService.java`
  - 폴링 기본 대상 포트 갱신: event 8081→8091, digitaltwin self 8084→8085. gateway 8090 유지.
  - @Value 기본값만 변경(yml override 여지 유지).

## 4. 수정 사유
- 프론트가 실제 호출하는 통합 API를 ADMIN JWT로 보호 → 화면 데이터 접근 통제.
- raw actuator는 localhost 전용으로 외부 노출 차단(포트 스캔·직접 접근 방지)하되 aggregator는 localhost로 폴링 유지.

## 5. 예상 변경 내용
- 추가: SecurityConfig 인가 규칙 1줄, yml management.server 설정(3개), aggregator @Value 기본 포트 변경.
- 변경: 없음(로직 변경 아님, 규칙/설정/기본값).
- 삭제: 없음.

## 6. 위험도
**MEDIUM**
- SecurityConfig 인가 규칙 순서(첫 매칭 우선) 오류 시 보호 실패 또는 과잉 차단 위험 → QA로 규칙 순서 검증.
- actuator 포트 분리로 배포 환경 포트/방화벽 매핑 변경 필요. localhost 바인딩은 **동일 호스트 배포 전제**(분산 배포 시 재설계).
- 프론트: 로그인 사용자가 ADMIN 역할이어야 화면 데이터 조회 가능.

## 7. 수정하지 않을 경우 문제
- 모니터링 정보가 무인증 공개로 유지되어 사용자 보안 요구 미충족.

## 8. 승인 요청
사용자 승인 완료(2026-07-01, 범위 전부). 진행: 04-A + 04-B(병렬) → 04-C → QA 검증.
후속: 프론트 "서버 관리 콘솔" ADMIN 전용 제한(react-frontend-agent) 여부는 연동 Loop에서.
