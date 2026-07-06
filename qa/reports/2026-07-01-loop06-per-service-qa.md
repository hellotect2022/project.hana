# QA 검증 보고서 — Loop 06 (06-A/06-B/06-C 통합) 서비스별 모니터링 재설계

- 작성일: 2026-07-01
- Loop: 06 (A: hana-common DTO / B: digitaltwin MonitorService / C: 프론트 2파일)
- 검증 AGENT: qa-code-reviewer
- 근거: `qa/change-requests/2026-07-01-loop06-per-service-monitoring.md`,
  `docs/reports/2026-07-01-loop06A-dto-redesign-report.md`,
  `-loop06B-monitorservice-report.md`, `-loop06C-frontend-redesign-report.md`
- 검증 대상 저장소: 백엔드 `d:\HDD\05-02.hanadream`, 프론트 `d:\HDD\05-06.hanadream_web_new`

## 전체 판정: PASS (경미 INFO 2건, 코드 수정 불필요)

Loop 06 종료 가능. 계약(엔드투엔드) 일치, 빌드/컴파일 통과, 범위 준수 확인.
후속 정리 항목 2건은 devops-agent 소관(무해).

---

## 1. 엔드투엔드 계약 일치 (최중요) — PASS

컨트롤러 `MonitorController.java:43-45`는 `ApiResponse<MonitorSnapshot>`을 반환하며,
record 컴포넌트명이 그대로 JSON camelCase 필드가 된다. 백엔드 최종 JSON 필드와 프론트
접근 경로를 대조.

| 계약 필드 | 백엔드 정의 | 프론트 접근 | 결과 |
|---|---|---|---|
| `dependencies[]` | `MonitorSnapshot.java:16` | `SystemServerPage.tsx:154-157` `data?.dependencies` | 일치 |
| `dependencies[].name/target/status` | `DependencyStatus.java:10-12` | `.tsx:202-210` `dep?.name/target/status` | 일치 |
| `services[]` | `MonitorSnapshot.java:15` | `.tsx:148-151` `data?.services` | 일치 |
| `services[].resource.diskUsage` | `ResourceUsage.java:17` | `.tsx:335` `resource?.diskUsage` | 일치 |
| `resource.processCpuLoad` | `ResourceUsage.java:13` | `.tsx:327` | 일치 |
| `resource.heapUsedBytes/heapMaxBytes` | `ResourceUsage.java:11-12` | `.tsx:331,379` | 일치 |
| `resource.systemCpuLoad/threadCount/uptimeMs` | `ResourceUsage.java:14-16` | `.tsx:358,363,370` | 일치 |
| `services[].pid/startedAt/responseTimeMs/detail` | `ServiceMonitor.java:14-19` | `.tsx:337,338,374,393` | 일치 |
| `host` (제거됨) | 없음 | 없음 | 일치 |

- `host` 잔존 참조: 프론트 `src` 전역 grep(`\.host\b|HostSummary|data\.host|resource\.host`) 0건.
- diskUsage 접근 경로 정확(`services[].resource.diskUsage`), dependencies 경로 정확.

## 2. 06-A hana-common DTO — PASS

- `ResourceUsage.java` record + `@Builder` 유지(:9-10), `diskUsage`가 기존 필드 뒤 마지막 배치(:17), 주석 0.0~1.0/-1.0 명시.
- `DependencyStatus.java` 신규 record + `@Builder`, `name/target/status(LiveStatus)`(:9-13) — 명세 일치.
- `MonitorSnapshot.java` host 제거, `List<DependencyStatus> dependencies` 추가(:16), `timestamp/services`+`@Builder` 유지.
- `HostSummary.java` 삭제 확인(glob 결과 부재). 소스 전역 `HostSummary` 참조는 docs/qa 보고서에만 존재하며 `**/*.java` 소스 0건.

## 3. 06-B MonitorService — PASS

- 제거 심볼 잔존참조 0건: `network-targets|networkTargets|collectHostSummary|collectHeapUsage|collectDbStatus|collectNetworkStatus|fetchHealth|taggedMetricValue|valueAsMap|parseNetworkTargets|HostSummary` grep → 소스 0건(application.yml의 `network-targets` 키 1건만, 아래 INFO).
- `collectDependencies()`(:233-249): 대상별 `isReachable` → UP/DOWN, 목록 비면 빈 리스트 반환. UNKNOWN은 파싱 실패 대상 제외로 자연 미발생(도달성 판정은 UP/DOWN 이분).
- `parseDependencyTargets()`(:347-383): 첫 `=`로 name 분리(:360-364, `eq>0 && eq<len-1` 방어), 이후 `lastIndexOf(':')`로 host:port 분리(:365), 포트 `NumberFormatException`/형식오류 warn 후 무시(:366-380). name 생략 시 host를 name으로(:374). 명세대로 동작.
- diskUsage: `collectResource(name, baseUrl, health)`가 health UP 경로에서만 호출(:122-134)되고 내부 `collectDiskUsage(health)`(:255-271)로 계산, 컴포넌트/필드 부재·total<=0·범위이탈 시 -1.0 센티넬. UP일 때만 수집·실패 시 -1.0 요건 충족.
- self(digitaltwin)도 `services` 3항목 중 하나(:80-82)로 resource(cpu/heap/disk) 채움. 별도 host 요약 없음.
- 추가 HTTP 호출 없이 pollService가 확보한 health 재사용(:134) — 설계 준수.

## 3b. hdt.monitor.network-targets yml 잔존 — INFO (무해)

`hana-digitaltwin/src/main/resources/application.yml:117` `network-targets: ["192.168.250.181:5432"]`
가 남아 있으나 코드는 더 이상 이 키를 읽지 않음(신 키 `hdt.monitor.dependencies` 사용,
코드 내 기본값 내장). 무해하나 정리 대상 → devops-agent 소관. 신 키 `hdt.monitor.dependencies`는
yml에 미기재이며 코드 기본값(`DB=...:5432,Kafka=...:9092`)으로 동작.

## 4. 06-C 프론트 — PASS

- dependencies map 렌더(:200-215): 라벨=name, 보조=target(title 포함), Badge tone은 `liveStatusView`(:47-56) UP=success/DOWN=danger/그외=warning. 빈 배열 시 "등록된 인프라 의존처가 없습니다."(:197-198), 로딩 분기(:195) 처리.
- 테이블 컬럼: CPU=`formatUsagePercent(resource.processCpuLoad)`(:327), 힙=`formatRatioPercent(heapUsedBytes,heapMaxBytes)`(:331) — `max<=0`/값없음/`used<0` 방어(:77-89) "측정 불가", 디스크=`formatUsagePercent(resource.diskUsage)`(:335).
- -1/측정불가 처리: `formatUsagePercent`가 `value<0`/비수치/NaN → "측정 불가"(:39-44). `resource==null`(서비스 DOWN) 시 CPU/힙/디스크 "—"(:327,330,335 `hasResource` 가드).
- 클릭 아코디언 토글: `expanded` state(:146) + `onToggle`(:258-262), 상세 행 `isOpen` 조건 렌더(:350-397). 접근성: `<button type="button" aria-expanded={isOpen}>`(:308-311) + caret ▸/▾(:314). resource 없으면 안내(:389-391).
- 보존 동작 회귀: ADMIN 가드(:128-133,160-174), 5초 폴링 `refetchInterval:POLL_INTERVAL_MS`(:34,138), `enabled:isAdmin`(:139), `retry:false`(:140), 403 인라인(:143,160), "최근 갱신"(:182-186), 표시명 매핑 `SERVICE_DISPLAY_NAME`(:28-32,246-247), 힙 각주(:271-276) 모두 유지.
- monitorService.ts: JSDoc typedef 갱신 확인(HostSummary 제거, DependencyStatus 추가:46-50, ResourceUsage.diskUsage:28, MonitorSnapshot.dependencies:57). fetch/unwrap/queryKey 로직 불변(:64-91).

## 5. 범위 준수 — PASS

- 06-A: `hana-common/src/.../monitor/dto/` 3파일만(ResourceUsage/DependencyStatus/MonitorSnapshot) + HostSummary 삭제.
- 06-B: `MonitorService.java` 단일 파일.
- 06-C: 프론트 `monitorService.ts`, `SystemServerPage.tsx` 2파일.
- 수정 금지 파일(build.gradle/application*.yml/package.json/tsconfig/vite.config 등) 무변경 — 신규 의존성 없음(MonitorService는 java.net.Socket / spring-web RestClient만 사용).

## 6. 컴파일/빌드 — PASS

- `./gradlew :hana-common:compileJava :hana-digitaltwin:compileJava -q` → EXIT 0.
- 프론트 `npm run typecheck`(tsc --noEmit) → EXIT 0.
- 프론트 `npm run build`(vite) → EXIT 0(chunk-size 500kB 경고는 기존 무관 경고).

---

## INFO / 후속 항목 (Loop 06 종료 무관, 코드 수정 불필요)

1. **[INFO-1] application.yml `hdt.monitor.network-targets` 잔존** — 코드 미사용, 무해.
   필요 시 제거 + `hdt.monitor.dependencies` 명시(override)는 **devops-agent 승인 게이트** 소관.
2. **[INFO-2] MonitorController `@Operation` description 문구 노후화** —
   `MonitorController.java:35-38`이 제거된 `host 요약`/`host.memoryUsage`/`DB·네트워크 상태`를
   여전히 서술(Swagger 문서 텍스트). 실제 JSON 계약은 정상이며 프론트는 Swagger 텍스트를
   소비하지 않으므로 런타임 무영향. 문서-코드 정합성 차원의 후속 정리 대상(스프링 백엔드 or docs).
   Loop 06 승인 편집 대상(파일 목록)에 포함되지 않아 이번 범위 밖.

## 후속 정책 명시 (승인 요청서/설계 준수 확인)

- `hdt.monitor.dependencies` yml override 필요 시 → devops-agent 승인 게이트.
- DB/Kafka 등 인프라 의존처는 **도달성(UP/DOWN)만** 수집, 리소스(CPU/디스크) 미수집 — 설계대로 구현됨.
- 기존 `network-targets` yml 정리 → devops-agent 소관(무해).

## 수정 요청 대상 AGENT / 재검증 필요 여부

- 수정 요청: **없음**(전체 PASS). INFO-1/INFO-2는 코드 수정 불필요, 후속 선택 항목.
- 재검증 필요: **없음**. Loop 06 종료 가능.
