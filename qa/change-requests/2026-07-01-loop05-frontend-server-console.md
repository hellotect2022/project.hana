# 코드 수정 승인 요청서 (프론트)

- 작성일: 2026-07-01
- Loop: 05
- 작성: loop-orchestrator
- 대상 저장소: 프론트엔드 위성 `d:\HDD\05-06.hanadream_web_new`
- 담당: react-frontend-agent

## 0. 배경
백엔드 Loop 02~04로 `GET /api/monitor/status`(ADMIN JWT 보호) + MonitorSnapshot 계약 완성.
프론트 "서버 관리 콘솔"(`/system/server`, GNB 메뉴·라우트 이미 존재, 페이지는 빈 템플릿)에 연동.

## 1. 수정/생성 대상
### 신규
- `src/services/monitorService.ts` — `fetchMonitorStatusAPI()` + `MONITOR_STATUS_QUERY_KEY`
### 수정
- `src/pages/system/SystemServerPage.tsx` (현재 빈 템플릿) — 상단 카드 + 서비스 테이블 + 5초 폴링
### (경미) ADMIN 가드
- 페이지 레벨 role 체크(AuthContext role 노출 시) 또는 401/403 graceful 처리. 라우팅 인프라가 role 가드를 지원하지 않으면 페이지 내 처리로 최소화.

## 2. 화면 매핑 (MonitorSnapshot → UI)
- 상단 카드 5개: `host.cpuUsage`, `host.memoryUsage`(=JVM 힙 사용률 라벨), `host.diskUsage`(×100 %), `host.dbStatus`, `host.networkStatus`(→ 정상/비정상 Badge)
- 서비스 테이블: `services[].service`(표시명 매핑), `status`(Running/Stopped Badge), `pid`, `resource.heapUsedBytes`(→MB), `startedAt`(시각), 관리=재시작 버튼 **disabled**
- 값 규약: 0.0~1.0 → ×100, `-1.0`="측정 불가", `LiveStatus` UP/DOWN/UNKNOWN 매핑

## 3. 기존 자산 활용
- API: `privateApi`(Bearer 자동), 응답 언랩 `unwrapData`
- 데이터패칭: `@tanstack/react-query` `useQuery({ refetchInterval: 5000 })`
- UI: `components/ui/Badge`(success/danger/warning), `components/ui/Button`(disabled), styled 테이블 패턴(UserManagePage)

## 4. 위험도
LOW~MEDIUM — 신규 service + 빈 페이지 채우기 중심. 라우팅/메뉴 인프라 변경은 최소화(가드는 페이지 레벨 우선).
package.json/vite.config/tsconfig 등 수정 금지 파일은 건드리지 않음(신규 의존성 없음).

## 5. 승인
사용자 "프론트 연동 구현 해줘"로 승인. react-frontend-agent가 05-06/src만 수정 → 작업 보고서 → QA.
