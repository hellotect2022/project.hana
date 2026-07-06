# AGENT 작업 보고서

## 1. 작업 AGENT
devops-agent

## 2. 작업 일시
2026-07-01

## 3. 작업 목표
Loop 04-B. 3개 서비스(gateway/event/digitaltwin)의 actuator(management) 서버를
localhost(127.0.0.1)에만 바인딩하여 외부 접근을 차단한다. aggregator(MonitorService)는
동일 호스트에서 localhost로 폴링하므로 정상 동작한다.
승인 요청서: `qa/change-requests/2026-07-01-loop04-monitor-admin-auth.md` (사용자 승인 완료).

## 4. 수정한 파일
- `hana-gateway/src/main/resources/application.yml`
  - `management.server` 아래 `address: 127.0.0.1` 추가 (기존 `port: 8090` 유지).
- `hana-event/src/main/resources/application.yml`
  - `management.server` 블록 신규 추가: `port: 8091`, `address: 127.0.0.1`
    (기존에는 management 전용 server 항목이 없어 메인 8081 공유 → 8091로 분리).
- `hana-digitaltwin/src/main/resources/application.yml`
  - `management.server` 블록 신규 추가: `port: 8085`, `address: 127.0.0.1`
    (기존에는 management 전용 server 항목이 없어 메인 8084 공유 → 8085로 분리).

## 5. 생성한 파일
- `docs/reports/2026-07-01-loop04B-actuator-bind-report.md` (본 보고서)

## 6. 주요 작업 내용
각 파일의 단일 `management:` 키 아래에 `server`(관리 서버 바인딩)를 형제 키로 두고
`endpoints`(노출)·`endpoint`(개별 설정)는 그대로 유지했다. 관리 서버 설정 외의 기존
설정값은 일절 변경하지 않았다.

최종 management 블록:
- gateway: `server.port 8090` + `server.address 127.0.0.1`
- event:   `server.port 8091` + `server.address 127.0.0.1`
- digitaltwin: `server.port 8085` + `server.address 127.0.0.1`

메인 애플리케이션 포트(gateway 8080 / event 8081 / digitaltwin 8084)는 변경하지 않았다.
이번 변경은 management 전용 관리 포트의 바인딩/포트 분리에 국한된다.

## 7. 기술적 판단 근거
- Spring Boot에서 `management.server.address`를 127.0.0.1로 지정하면 actuator 관리 서버가
  루프백 인터페이스에만 바인딩되어 외부 네트워크에서 직접 접근이 차단된다.
- event/digitaltwin은 기존에 관리 서버가 메인 포트를 공유했으므로, 메인 서비스 포트를
  건드리지 않고 actuator만 localhost로 격리하려면 전용 관리 포트 분리가 필요하다.
- gateway는 이미 관리 포트(8090)가 분리되어 있어 `address`만 추가하면 충분하다.
- 검증: `git diff`로 의도한 추가만 반영됨을 확인, `python yaml.safe_load`로 3개 파일 파싱 OK.

### git diff 구분 (이전 미커밋분 vs 이번 04-B 추가분)
- 이번 04-B 추가분(이번 작업 산출물):
  - gateway: 하단 `management.server.address: 127.0.0.1`
  - event: 하단 `management.server` 블록(port 8091, address)
  - digitaltwin: 하단 `management.server` 블록(port 8085, address)
- 이전 Loop(02-B/03-A 등) 미커밋 변경분(이번 작업에서 건드리지 않음, diff에 함께 표시됨):
  - gateway: `spring.main.web-application-type` none → reactive (주석 포함)
  - event: `storage.postgres.host` 88 → 181 주석 토글, `spring.kafka.bootstrap-servers`
    주석 토글
  - digitaltwin: `logging.level` hibernate 블록, `app.building-lighting` 블록 추가
  → 위 항목들은 이번 04-B와 무관한 기존 미커밋 상태이며 본 작업으로 변경하지 않았다.

## 8. 다른 AGENT 영역 영향 여부
있음 — 변경 요청/후속 조치 필요.
- Loop 04-C(spring-backend-agent): aggregator(MonitorService) 폴링 기본 대상 포트를
  갱신해야 함. event 8081 → 8091, digitaltwin self 8084 → 8085. gateway는 8090 유지.
  본 변경으로 event/digitaltwin actuator가 메인 포트에서 전용 관리 포트로 이동했으므로,
  04-C 미반영 시 aggregator 폴링이 기존 포트로 향해 헬스 수집이 실패한다.
- 프론트(react-frontend-agent): 직접 영향 없음(통합 API 경유). 단, 04-A의 ADMIN JWT
  보호와 연동되는 화면 접근 제한은 별도 Loop에서 다룬다.

## 9. 남은 이슈 / 위험
- localhost(127.0.0.1) 바인딩은 aggregator와 각 서비스가 동일 호스트에 배포된다는 전제에
  의존한다. 서비스가 서로 다른 호스트로 분산 배포되면 aggregator가 원격 actuator에 접근할
  수 없어 폴링이 실패한다 → 분산 배포 시 바인딩/폴링 방식 재설계 필요(위험으로 기록).
- 배포 환경(방화벽/컨테이너 포트 매핑)에서 event 8091, digitaltwin 8085 관리 포트를
  외부 노출하지 않도록 유지해야 한다(localhost 바인딩이 1차 방어).

## 10. QA 요청 사항
- 3개 yml의 `management:` 아래 `server`/`endpoints`/`endpoint`가 형제 키로 올바르게
  병합되었는지(들여쓰기) 검증.
- 기존 설정값 무변경 여부(메인 포트 8080/8081/8084 유지, endpoints/endpoint 유지) 검증.
- 04-B 단독으로는 aggregator 폴링 포트(04-C)가 아직 미갱신이므로, 04-C 완료 전까지는
  event/digitaltwin 헬스 수집 실패가 예상됨을 QA에서 인지(04-C와 묶어 통합 검증 권장).

## 11. 다음 Loop 제안
- Loop 04-C: MonitorService @Value 기본 폴링 포트 갱신(event 8091, self 8085).
- 04-A/04-B/04-C 통합 후 로컬 기동 스모크 테스트(actuator 외부 차단 + aggregator localhost
  폴링 정상 + /api/monitor/** ADMIN JWT 보호) 검증.

## 롤백 방안
각 파일에서 이번에 추가한 `management.server` 하위 항목만 제거하면 원복된다.
- gateway: `address: 127.0.0.1` 라인 삭제.
- event: `server: { port: 8091, address: 127.0.0.1 }` 블록 삭제(actuator가 메인 8081 공유로 복귀).
- digitaltwin: `server: { port: 8085, address: 127.0.0.1 }` 블록 삭제(메인 8084 공유로 복귀).
`git checkout -- <파일>`은 동일 파일의 이전 Loop 미커밋 변경분까지 되돌리므로 사용하지 말 것.
