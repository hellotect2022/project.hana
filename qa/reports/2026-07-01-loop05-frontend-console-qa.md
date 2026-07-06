# QA 검증 보고서 — Loop 05 프론트 연동 (서버 관리 콘솔)

- 작성일: 2026-07-01
- 검증자: qa-code-reviewer (검증 전용, 코드 무수정)
- 대상 저장소: 프론트 위성 `d:\HDD\05-06.hanadream_web_new`
- 근거 문서
  - 승인 요청서: `qa/change-requests/2026-07-01-loop05-frontend-server-console.md`
  - 작업 보고서: `docs/reports/2026-07-01-loop05-frontend-console-report.md`
- 검증 대상 파일
  - 신규: `d:\HDD\05-06.hanadream_web_new\src\services\monitorService.ts`
  - 수정: `d:\HDD\05-06.hanadream_web_new\src\pages\system\SystemServerPage.tsx`

---

## 전체 판정: **PASS (조건부 — WARNING 후속 항목 2건 동반)**

2개 대상 파일은 백엔드 계약과 정합하며 타입체크(tsc --noEmit)를 통과한다. role 필드
정합성(최중요 항목)은 **대상 파일 기준 PASS**다. 다만 아래 후속 항목(라우트 레벨 ADMIN
가드 부재, Sidebar `role` 필드 버그, 재시작 백엔드 엔드포인트 부재)은 이번 Loop 범위 밖의
기존/구조적 이슈로 WARNING 처리하며 Loop 05 종료는 가능하다.

---

## 1. role 필드 정합성 (최중요) — **PASS**

### 계약 확정 (백엔드 실 코드 근거)
- `/auth/login`, `/auth/me` 두 엔드포인트 모두 `LoginResponse.UserInfo`를 반환한다.
  - `AuthController.java:56` 로그인 → `ApiResponse<LoginResponse>` (`userInfo` 필드)
  - `AuthController.java:152-159` `/me` → `ApiResponse<LoginResponse.UserInfo>`
- `LoginResponse.UserInfo`(LoginResponse.java:19-24)는 `List<String> roles` **복수형
  문자열 배열** 이다. (roleId/roleName 객체가 아니라 **평문 문자열 리스트**)
- 값 출처: `AuthService.toUserInfo()`(AuthService.java:184-191) → `user.roleNames()`
- `User.roleNames()`(User.java:116-120)는 `role.getRoleName()` 문자열 리스트를 반환.
  즉 각 원소는 `"ADMIN"` 같은 역할명 문자열.

### 프론트 AuthContext 제공 필드
- `AuthContext.tsx:14` `/auth/me` 응답 `res.data.data`를 `setUser`.
- `AuthContext.tsx:29` 로그인 시 `res.data.data.userInfo`를 `setUser`.
- 따라서 `user` 객체에는 **`roles`(복수, `string[]`)** 필드가 존재한다. `role`(단수)은
  존재하지 않는다.

### SystemServerPage 가드 로직 검증 (SystemServerPage.tsx:92-97)
```ts
const roles: string[] = Array.isArray(user?.roles)
  ? user.roles
  : Array.isArray(user?.role)   // fallback (실제로는 도달하지 않음)
    ? user.role
    : [];
const isAdmin = roles.some((r) => String(r).toUpperCase() === "ADMIN");
```
- 1순위로 `user.roles`(실제 제공 필드)를 읽음 → **정합**. 가드가 무력화되거나 항상
  차단되지 않는다.
- `user.role` fallback은 안전망(dead branch)일 뿐 오작동을 유발하지 않는다.
- 비교값 `"ADMIN"`은 `roleName` 문자열과 일치. 백엔드 `SecurityConfig.java:53`
  `.requestMatchers("/api/monitor/**").hasRole("ADMIN")` 와도 의미상 정합
  (Spring `hasRole("ADMIN")` = authority `ROLE_ADMIN`, DB roleName은 `ADMIN`).
- 판정: **PASS**. (Sidebar가 읽는 `user?.role?.[0]`는 잘못된 필드지만 이는 별개 파일의
  기존 버그 — 아래 후속 항목 참조.)

---

## 2. monitorService.ts — **PASS**

| 점검 | 결과 | 근거 |
|---|---|---|
| privateApi import 형태 | PASS | `import privateApi from "./api"` — `api.ts:118` `export default privateApi`와 일치(default export) |
| 경로 최종값 | PASS | `privateApi.get("/monitor/status")`, baseURL=`/api`(api.ts:10) → 최종 `/api/monitor/status`. 백엔드 `MonitorController @RequestMapping("/api/monitor") + @GetMapping("/status")`와 일치 |
| unwrapData 재사용 적절성 | PASS | `{ data: ... }` 래핑을 안전하게 언랩. `ApiResponse<MonitorSnapshot>` 구조와 정합. null/비객체 방어 있음 |
| skipApiErrorModal 플래그 연동 | PASS | `api.ts:30,41` `config.skipApiErrorModal` 실제 참조 → 응답/에러 인터셉터에서 전역 모달 skip |
| skipGlobalLoading 플래그 연동 | PASS | `api.ts:54,61,67` `config.skipGlobalLoading` 실제 참조 → 로딩 인디케이터 skip. 5초 폴링에 적절 |
| 캐스팅 | PASS | 커스텀 플래그를 `as AxiosRequestConfig`로 전달, 주석으로 사유 명시 |

주: 파일은 `.ts`이나 타입은 JSDoc `@typedef`로만 표현되고 함수 시그니처에 TS 타입
주석이 없다(`res`, 반환 `any` 사실상). tsc 오류는 없으나, 소비 측
`SystemServerPage`에서 `data?.host` 등이 타입 추론 없이 사용된다(런타임 옵셔널 체이닝으로
방어). 기능상 문제 아님 — 개선 여지(WARNING 아님, 참고).

---

## 3. SystemServerPage.tsx 표시 로직 — **PASS**

| 점검 | 결과 | 근거 |
|---|---|---|
| 사용률 ×100 + `-1.0`="측정 불가" | PASS | `formatUsagePercent`(38-43): `value < 0` → "측정 불가", 그 외 `*100 .toFixed(1)+"%"`. NaN/비수치도 방어 |
| LiveStatus→Badge tone (UP/DOWN/UNKNOWN) | PASS | `liveStatusView`(46-55) UP=success/DOWN=danger/그외=warning. tone 값 모두 `BadgeTone`(Badge.tsx:12)에 존재 |
| 서비스 status Badge | PASS | `serviceStatusView`(58-67) Running/Stopped/Unknown 매핑 |
| heapUsedBytes→MB | PASS | `formatHeapMB`(70-73): `/1048576 .toFixed(0)+"MB"`, `<0`/NaN→"—" |
| startedAt 포맷 | PASS | `formatDateTime`(76-80): 유효 ISO→`toLocaleString("ko-KR")`, 무효→"—" |
| null→"—" | PASS | pid(222), heap(225), startedAt(227) 모두 "—" fallback |
| 서비스 표시명 매핑(미매핑 fallback) | PASS | `SERVICE_DISPLAY_NAME[svc.service] ?? svc.service ?? "—"`(209). 매핑 시 원 이름 병기(214-216) |
| 재시작 버튼 disabled | PASS | `<Button ... disabled title="재시작 기능은 준비 중입니다.">`(229-236) |
| 5초 refetchInterval | PASS | `POLL_INTERVAL_MS=5000`(33), `refetchInterval`(102) |
| 로딩 처리 | PASS | 카드 `…`(171), 테이블 "불러오는 중…"(188-189) |
| 에러 처리 | PASS | `isError && !forbidden`(157) 인라인 안내 |
| 403 처리 | PASS | `getHttpStatus(error)===403`(107), `enabled: isAdmin`+`retry:false`(103-104), 비ADMIN/403 → 권한 안내 화면(128-142) |
| import 정합 | PASS | `AdminPageTemplate`(title/description/children props 일치), `Badge/Button/BadgeTone`(ui/index.ts export 확인) |

세부 정합: 계약의 `host{cpuUsage,memoryUsage,diskUsage,dbStatus,networkStatus}`,
`services[]{service,status,pid,startedAt,resource.heapUsedBytes}`를 화면이 정확히 참조.
`memoryUsage`를 "힙 사용률"로 라벨링(119) — 백엔드 주석/DTO 의미와 정합.

---

## 4. 수정 금지 파일 무변경 — **PASS**

`git status --short`(프론트) 결과, 변경/미추적 목록에 다음 파일 **없음**:
`package.json`, `package-lock.json`, `vite.config.ts`, `tsconfig.json`, `.github/`.
신규 의존성 도입 없음. 사용 라이브러리(axios, @tanstack/react-query, styled-components)는
모두 기존 코드에서 사용 중.

---

## 5. 빌드/타입 검증 — **PASS**

- 실행: `npx tsc --noEmit` (프론트 저장소) → **EXIT=0, 오류 없음**.
- 작업자 보고(PASS)와 일치.

---

## 6. 범위 준수 — **PASS (대상 파일 한정) / 주의**

- 이번 Loop 대상 2개 파일(`monitorService.ts` 신규, `SystemServerPage.tsx` 수정)은
  요구 범위 내이며 `05-06/src` 안에 있다.
- **주의(정보성)**: 프론트 작업트리에는 다른 도메인의 다수 미커밋 변경이 함께 존재한다
  (Sidebar, deviceService 등 40여 파일 및 미추적 디렉터리). 이는 Loop 05 산출물이 아닌
  기존 미정리 변경으로 판단되며, 커밋 시 **Loop 05 변경만 분리 커밋**할 것을 권고한다.
  (판정에는 미반영 — 대상 2개 파일 자체는 범위 준수)

---

## 후속 항목 (WARNING / 별도 Loop)

### F-1. Sidebar `role` 필드 버그 — **WARNING (기존 버그, 담당: react-frontend-agent)**
- `src/components/layout/Sidebar.tsx:74` `{user?.role?.[0]}`.
- 실제 제공 필드는 `roles`(복수)이므로 `user.role`은 항상 `undefined` → 프로필 배지에
  역할명이 표시되지 않음(빈 배지). 기능 장애는 아니나 표시 버그.
- 조치: `user?.roles?.[0]`로 수정 필요. Loop 05 범위 밖 → 별도 변경 요청서 권고.

### F-2. 라우트 레벨 ADMIN 가드 부재 — **WARNING (구조적, react-frontend-agent)**
- `menuData.tsx:163` / `AppRouter.tsx:18` `/system/server` 라우트에 역할 가드 없음.
- 현재는 페이지 컴포넌트 내부 가드(비ADMIN 인라인 안내) + 서버 403으로 이중 방어되어
  실질 데이터 노출 위험은 없음. 다만 라우트 레벨 가드가 없어 비ADMIN도 진입은 가능.
- 조치: 라우트 레벨 ADMIN 가드(Route guard) 추가는 후속 Loop 권고.

### F-3. 재시작(restart) 백엔드 엔드포인트 부재 — **INFO (설계상 의도)**
- 재시작 버튼은 `disabled`로 비활성(준비 중). 백엔드에 서비스 재시작 API 없음.
- 조치: 실제 재시작 기능 필요 시 백엔드 엔드포인트 설계 후속 Loop 필요
  (담당: spring-backend-agent/devops-agent). 이번 Loop에서는 UI 자리표시로 정상.

---

## 수정 요청 대상 AGENT

- 이번 Loop 대상 2개 파일: **수정 요청 없음 (PASS)**.
- 후속(별도 Loop): F-1 Sidebar → `react-frontend-agent` (변경 요청서 권고),
  F-2 라우트 가드 → `react-frontend-agent`, F-3 재시작 API → 백엔드 계열 AGENT.

## 재검증 필요 여부

- 대상 2개 파일 재검증 **불필요** (PASS).
- F-1/F-2/F-3는 각각 별도 Loop 착수 시 해당 산출물에 대해 신규 검증.

## Loop 05 종료 가능 여부

**종료 가능 (PASS).** 대상 산출물이 계약 정합 + 타입체크 통과 + role 가드 정합.
후속 항목(F-1~F-3)은 위험도/담당을 기록해 별도 Loop로 이관.
