# 작업 보고서 — Loop 05 프론트 서버 관리 콘솔 연동

- 작성일: 2026-07-01
- Loop: 05
- 담당 AGENT: react-frontend-agent
- 대상 저장소: 프론트엔드 위성 `d:\HDD\05-06.hanadream_web_new`
- 승인 요청서: `qa/change-requests/2026-07-01-loop05-frontend-server-console.md`

## 1. 작업 개요
백엔드 `GET /api/monitor/status`(ADMIN JWT 보호, `MonitorSnapshot` 계약)를 프론트 "서버 관리
콘솔"(`/system/server`)에 연동. 서비스 클라이언트 신규 작성 + 빈 페이지 템플릿 구현.
5초 폴링, 상단 카드 5개 + 서비스 테이블, 비ADMIN 안내 처리.

## 2. 변경 파일 목록
### 신규
- `src/services/monitorService.ts`
  - `fetchMonitorStatusAPI()` — `privateApi.get("/monitor/status")` + `unwrapData`
  - `MONITOR_STATUS_QUERY_KEY = ["system","monitor","status"]`
  - JSDoc typedef: `MonitorSnapshot / ServiceMonitor / HostSummary / ResourceUsage / LiveStatus`
  - 커스텀 axios 플래그(`skipApiErrorModal`, `skipGlobalLoading`)로 403 을 전역 모달 대신
    인라인 처리하도록 요청 설정.

### 수정
- `src/pages/system/SystemServerPage.tsx` (빈 `AdminPageTemplate` → 실제 콘솔)

## 3. 화면 구성 요약
- **폴링**: `useQuery({ queryKey: MONITOR_STATUS_QUERY_KEY, queryFn, refetchInterval: 5000,
  enabled: isAdmin, retry: false })`.
- **상단 카드 5개**:
  1. CPU 사용률 = `host.cpuUsage ×100%`
  2. **힙 사용률** = `host.memoryUsage ×100%` (라벨을 "메모리"가 아닌 "힙 사용률"로 표기 —
     백엔드 memoryUsage 는 JVM 힙 사용률이므로)
  3. 디스크 사용률 = `host.diskUsage ×100%`
  4. DB 연결 = `host.dbStatus` → Badge
  5. 네트워크 = `host.networkStatus` → Badge
  - `-1.0` 사용률은 "측정 불가" 표기.
  - LiveStatus → Badge: UP=success"정상" / DOWN=danger"비정상" / UNKNOWN=warning"확인불가".
- **서비스 테이블**: 서비스명(표시명 매핑, 원 이름 보조표기) / 상태(Running=success,
  Stopped=danger, Unknown=warning) / PID / 메모리(힙, `heapUsedBytes/1048576 → NNMB`) /
  시작 시각(`startedAt` 로컬 포맷) / 관리(재시작 버튼 **disabled**).
  - `pid`/`startedAt`/`heapUsedBytes` null·미측정 시 "—".
  - 표시명 매핑(페이지 내 상수): `hana-digitaltwin→API 서버`, `hana-gateway→게이트웨이`,
    `hana-event→이벤트 서버`. 매핑에 없으면 원 이름 그대로.
- **상태 처리**: 로딩("불러오는 중…"), 에러 배너, 빈 목록 안내.

## 4. ADMIN 가드
- `AuthContext` 의 `user.roles`(백엔드 `UserInfo.roles: List<String>`, 값 예: "ADMIN")로
  페이지 레벨 ADMIN 여부 판정. `roles` 부재 시 `user.role` 도 폴백 확인.
- 비ADMIN 이면 쿼리를 `enabled: false` 로 두고 "관리자 권한이 필요합니다" 안내만 노출.
- 서버 403(권한 부족) 응답도 동일 안내로 graceful 처리. `monitorService` 에서
  `skipApiErrorModal`/`skipGlobalLoading` 을 지정해 기존 전역 에러 모달과 충돌하지 않게 함.

## 5. 백엔드 API 계약 영향
- **없음.** 기존 `GET /api/monitor/status` / `MonitorSnapshot` 계약을 그대로 소비.
  백엔드 코드/DTO/SecurityConfig 미변경.

## 6. 검증
- `npm run typecheck` (tsc --noEmit): PASS
- `npm run build` (vite build): PASS (825 modules, 빌드 성공)
  - chunk 크기 경고는 프로젝트 전역 기존 이슈로 본 작업과 무관.
- 수정 금지 파일(package.json / package-lock / vite.config.ts / tsconfig.json) 미변경,
  신규 의존성 추가 없음.

## 7. 다른 AGENT 영향 / 후속 항목
- 백엔드 무관.
- **memoryUsage=힙 라벨 처리**: 상단 카드 2번 라벨을 "힙 사용률"로 명시함(백엔드 계약상
  host.memoryUsage 는 JVM 힙 사용률).
- **라우트 레벨 ADMIN 가드 미구현(후속)**: 현재는 페이지 레벨 role 체크 + 403 graceful
  처리로 갈음. 라우터/`menuData` 대공사를 피하기 위함. 라우트 진입 자체를 막는 ADMIN 가드는
  후속 Loop 로 제안.
- **재시작 버튼 비활성**: 백엔드 재시작 API 미제공. 활성화하려면 백엔드 제어 엔드포인트
  신규 계약이 필요(변경 요청서 대상).
- (참고, 본 작업 범위 밖) `Sidebar.tsx` 는 `user?.role?.[0]` 을 읽으나 백엔드 계약 필드는
  `roles` 임. 프로필 뱃지 표기 버그 가능성 — 필요 시 별도 Loop 로 정정 제안.

## 8. QA 요청 항목
- `src/services/monitorService.ts`: 쿼리 키/언랩/커스텀 플래그 설정 적절성.
- `src/pages/system/SystemServerPage.tsx`: 사용률 ×100·-1.0 처리, LiveStatus→Badge 매핑,
  힙 MB 변환, null "—" 처리, 서비스 표시명 매핑, 비ADMIN/403 안내, 5초 폴링.
- 수정 금지 파일 무변경 확인.
