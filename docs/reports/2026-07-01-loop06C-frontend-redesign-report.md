# 작업 보고서 — Loop 06-C 프론트엔드 서버 관제 화면 재설계

- 작성일: 2026-07-01
- Loop: 06-C
- AGENT: react-frontend-agent
- 승인 요청서: `qa/change-requests/2026-07-01-loop06-per-service-monitoring.md`
- 대상 저장소: `05-06.hanadream_web_new` (프론트 위성)
- 배경: 백엔드 06-A/06-B 에서 `GET /api/monitor/status` 응답 계약 변경
  (`host` 요약 제거 → `dependencies[]` 추가, `resource.diskUsage` 추가).

---

## 1. 변경 요약

백엔드 계약 변경에 맞춰 서버 관제 화면을 재설계했다.

- 상단 카드: 기존 host 요약(CPU/힙/디스크/DB/네트워크) 5개 고정 카드를 제거하고,
  `data.dependencies` 를 map 한 **인프라 의존처 도달성 카드**로 대체.
  (DB·Kafka 등은 리소스를 수집하지 않고 UP/DOWN/UNKNOWN 도달성만 Badge 로 표시)
- 서비스 테이블: **서비스별 리소스** 컬럼 구성으로 재편
  (서비스명 / 상태 / CPU / 힙 / 디스크 / PID / 시작시각 / 관리).
- 서비스명 클릭 시 **아코디언 상세 행**으로 systemCpuLoad / threadCount / uptime /
  responseTimeMs / heapUsed·heapMax(MB) / startedAt / detail 노출.
- monitorService.ts JSDoc typedef 갱신(HostSummary 제거, DependencyStatus 추가,
  ResourceUsage 에 diskUsage 추가). fetch/unwrap/queryKey 로직은 불변.

기존 동작(ADMIN 가드, 5초 폴링 refetchInterval, enabled:isAdmin, retry:false,
403 인라인 안내, "최근 갱신" 표기, 서비스 표시명 매핑, 힙 각주)은 모두 그대로 보존.

---

## 2. 변경 파일 목록

| 파일 | 변경 내용 |
|---|---|
| `src/services/monitorService.ts` | JSDoc typedef 갱신: `HostSummary` 제거, `DependencyStatus`(name/target/status) 추가, `MonitorSnapshot.host` → `dependencies`, `ResourceUsage`에 `diskUsage` 추가. 헤더 주석 정리. 로직 변경 없음. |
| `src/pages/system/SystemServerPage.tsx` | 상단 카드를 dependencies 기반으로 재설계, 서비스 테이블 컬럼 재편(CPU/힙/디스크 추가), 서비스명 클릭 상세 아코디언 행 추가. 신규 헬퍼 `formatRatioPercent` / `formatUptime` / `formatMs` 추가. 각주 갱신. |

신규 의존성/설정 파일(package.json, vite.config, tsconfig) 변경 없음.

---

## 3. 화면 구성 요약

### 상단 인프라 카드 (`dependencies`)
- 각 의존처 = 카드 1개.
  - 라벨 = `name`(예 "DB", "Kafka"), 보조로 `target`("host:port") 작게.
  - 값 = `liveStatusView(status)` Badge (UP=success"정상" / DOWN=danger"비정상" / 그외 warning"확인불가").
- dependencies 비었으면 "등록된 인프라 의존처가 없습니다." 안내.

### 서비스 테이블 컬럼
| 컬럼 | 소스 / 포맷 |
|---|---|
| 서비스명 | `SERVICE_DISPLAY_NAME` 매핑, 클릭 가능한 button(role, aria-expanded) |
| 상태 | `serviceStatusView(status)` Badge (Running/Stopped/Unknown) |
| CPU | `formatUsagePercent(resource.processCpuLoad)` (프로세스 CPU) |
| 힙 | `formatRatioPercent(heapUsedBytes, heapMaxBytes)` (max<=0/값없음 → "측정 불가") |
| 디스크 | `formatUsagePercent(resource.diskUsage)` |
| PID | `svc.pid` (없으면 "—") |
| 시작 시각 | `formatDateTime(startedAt)` |
| 관리 | 재시작 버튼(disabled, 준비 중) |
- `resource == null`(서비스 DOWN) 인 경우 CPU/힙/디스크 칸은 "—".

### 서비스명 클릭 상세 (아코디언 행)
- colSpan 상세 패널에 systemCpuLoad(%) / threadCount / uptime("Nd Nh Nm") /
  responseTimeMs(ms) / heapUsed·heapMax(MB) / startedAt 표시, `detail` 있으면 하단 노트.
- 접근성: 서비스명은 `<button type="button" aria-expanded>` + caret(▸/▾) 토글.
- resource 없으면 "리소스 정보가 없습니다(서비스 미응답 또는 수집 실패)." 안내.

---

## 4. 백엔드 API 계약 영향

- **소비만 함(변경 없음).** `GET /api/monitor/status → { data: MonitorSnapshot }` 을 그대로 소비.
- 소비하는 필드:
  - `dependencies[]`: { name, target, status(UP/DOWN/UNKNOWN) }
  - `services[]`: { service, status, responseTimeMs, resource, detail, pid, startedAt }
  - `services[].resource`: { heapUsedBytes, heapMaxBytes, processCpuLoad, systemCpuLoad,
    threadCount, uptimeMs, diskUsage }
- DB/Kafka 등 인프라 의존처는 **도달성만 표시**하며 리소스(resource)는 요구하지 않는다.
- 백엔드 코드/DB/Kafka 변경 요청 없음.

---

## 5. 검증

- `npm run typecheck` (tsc --noEmit): 통과(에러 0).
- `npm run build` (vite build): 성공. (chunk-size 500kB 경고는 기존부터 있던 무관 경고)
- 새 의존성/빌드 설정 변경 없음.

---

## 6. 다른 AGENT 영향

- 백엔드(spring-backend-agent) 계약을 **소비만** 한다. 백엔드 수정 요청 없음.
- DB/Kafka 는 도달성(status)만 표시하며 리소스 칸 없음(계약대로).

---

## 7. QA 요청 항목

1. dependencies 배열 렌더 정확성(라벨=name, 보조=target, Badge tone 매핑).
2. dependencies 빈 배열 시 안내 문구 노출.
3. 서비스 테이블 CPU=processCpuLoad, 힙=heapUsed/heapMax 비율, 디스크=diskUsage 매핑.
4. heapMaxBytes<=0 또는 resource 없음 시 "측정 불가"/"—" 처리.
5. 서비스명 클릭 아코디언 토글 및 상세 필드 포맷(uptime/responseTimeMs/heap MB).
6. 접근성: 서비스명 button/aria-expanded.
7. 보존 동작 회귀: ADMIN 가드, 5초 폴링, 403 인라인 안내, "최근 갱신", 힙 각주.
