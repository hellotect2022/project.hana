# QA 검증 보고서 — Loop 07-B 프론트 디바이스 active 일괄 처리 통합

- 작성일: 2026-07-01
- 검증자: qa-code-reviewer
- 대상 Loop: 07-B (react-frontend / `05-06.hanadream_web_new`)
- 근거 문서:
  - 승인 요청서 `qa/change-requests/2026-07-01-loop07-device-active-bulk.md`
  - 작업 보고서 `docs/reports/2026-07-01-loop07B-frontend-bulk-active-report.md`
  - 백엔드 계약(07-A) QA `qa/reports/2026-07-01-loop07A-device-active-bulk-qa.md`
- 검증 대상 파일(모두 수정, 신규 없음):
  - `05-06.hanadream_web_new/src/viewer/data/deviceService.ts`
  - `05-06.hanadream_web_new/src/viewer/hooks.ts`
  - `05-06.hanadream_web_new/src/viewer/ui/DeviceSidebar.tsx`

## 전체 판정: PASS

Loop 07 종료 가능. 수정 요청 대상 AGENT 없음. 재검증 불필요.
후속(비차단) 항목 3건은 6절 참조.

---

## 1. 항목별 검증 결과

### 1) 계약 일치 (07-A) — PASS
- 경로: `patchDeviceActiveBulk` 가 `api.patch('/device/active', body)` 호출
  (`deviceService.ts:60`). base URL `/api`(`client.ts:5`) → 최종 `PATCH /api/device/active`.
  07-A 계약(`DeviceController.@PatchMapping("/active")`)과 정확히 일치.
- body: `BulkActiveRequest = { scope:'ALL'|'CATEGORY'|'DEVICES', categoryId?:number,
  deviceIds?:number[], active:boolean }`(`deviceService.ts:53-58`). 07-A `BulkActiveRequest`
  DTO(scope enum, categoryId nullable, deviceIds nullable, active) 필드·타입·옵셔널성 일치.
- 응답: `ApiResponse<number>` 의 `data.data`(=updatedCount)를 `number` 로 반환
  (`deviceService.ts:59-62`). 07-A 응답 `ApiResponse<Integer>`(updatedCount)와 일치.
- 반환값(updatedCount) 자체는 UI 에서 직접 표시하지 않으나(성공/실패만 처리) 계약상 무해.
  07-A 가 `data:0`(카테고리 매칭 없음)을 예외가 아닌 정상 200 으로 응답하는데, 프론트는
  이를 성공으로 간주하고 캐시 무효화만 수행 → 트리 자동 재갱신되므로 사용자에게 이상 없음.

### 2) 개별 토글 이관 / 회귀 — PASS
- `onToggleActive`(`DeviceSidebar.tsx:211`) → `setActive.mutate({deviceId, active})` →
  `useSetActive` 내부가 `patchDeviceActiveBulk({scope:'DEVICES', deviceIds:[deviceId], active})`
  로 교체됨(`hooks.ts:73-77`). 호출부 시그니처(`{deviceId, active}`) 무변경.
- 캐시 무효화: `onSuccess → invalidateQueries(['devices','all'])`(`hooks.ts:76`). 기존과 동일 키.
- 3D 반영 경로 회귀 없음: 씬 가시성은 `device.active` 로 판정
  (`DeviceInstance.tsx:18` `sd && device.active && isFloorVisible(...)`). bulk 응답 후
  `['devices','all']` 무효화 → `useAllDevices` refetch → active 갱신값 전파. 기존과 동일하게
  비활성 장비 3D 숨김/표시 반영. 단건 PATCH → bulk 교체로 인한 동작 차이 없음.
- 미사용 alert 등 부작용 없음(개별 토글은 실패 시 별도 onError 미지정 — 기존 동작 유지).

### 3) 카테고리 분기 (CATEGORY↔DEVICES 폴백) — PASS
- `collectDevices(node)`(`DeviceSidebar.tsx:43-47`): 자기+자손 재귀 수집. 깊은 계층 방어 —
  트리 구축이 device 를 정확히 한 리프에만 push(`buildTree` `node.devices.push(d)`,
  `DeviceSidebar.tsx:28`)하므로 재귀 수집 시 **중복 deviceId 없음**. 재귀 깊이는 카테고리
  path(대>중>소) 수준으로 유한, 순환 없음.
- `singleCategoryId(devs)`(`DeviceSidebar.tsx:49-57`): 모든 device 가 동일 non-null
  `categoryId` 면 그 id, 하나라도 null 이거나 서로 다르면 `null`. 정확.
- 분기(`DeviceSidebar.tsx:219-222`):
  - `catId != null` → `{scope:'CATEGORY', categoryId:catId, active}`. 전송 categoryId 는
    device 의 소분류(leaf) id. 07-A `resolveSmallCategoryIdsForFilter` 는 대/중/소 어느
    레벨이든 하위 소분류로 cascade 하며, 소분류 id 입력 시 자기 자신으로 해석 → 대상 일치.
    따라서 "단일 소분류 노드" 에서만 CATEGORY 사용되고 결과 정확.
  - `catId == null`(상위 노드·혼합) → `{scope:'DEVICES', deviceIds, active}` 폴백. 백엔드
    cascade 의존 없이 정확 동작.
- **빈 deviceIds 방어**: `CatBtn` 은 `c.total > 0` 일 때만 렌더(`DeviceSidebar.tsx:150`),
  `onToggleCategory` 는 `if (!devs.length) return`(`DeviceSidebar.tsx:218`). DEVICES 폴백
  경로의 `deviceIds` 는 항상 길이 ≥ 1. 빈 배열 요청이 백엔드로 나가는 케이스 없음
  (07-A 는 빈 deviceIds 를 400 처리하나, 프론트가 애초에 보내지 않음).
- `busy` 재진입 가드(`DeviceSidebar.tsx:216`)로 처리 중 중복 발사 차단.

### 4) 전체 버튼 (ALL) — PASS
- `onToggleAll(true/false)` → `{scope:'ALL', active}`(`DeviceSidebar.tsx:228-233`). categoryId/
  deviceIds 미포함(옵셔널) → 07-A ALL 분기(WHERE 없는 전체 UPDATE)와 일치.
- busy disabled: 헤더 두 버튼 모두 `disabled={busy}`(`DeviceSidebar.tsx:255-256`),
  `busy = setActive.isPending || setActiveBulk.isPending`(`DeviceSidebar.tsx:210`). 개별/
  카테고리/전체 뮤테이션 어느 것이든 진행 중이면 전체 버튼 비활성 → 동시 발사 방지.
- 실패 피드백: `onError → alert('전체 일괄 처리에 실패했습니다.')`(`DeviceSidebar.tsx:230-232`).
  카테고리도 `alert(...)`(`DeviceSidebar.tsx:224`). 기존 최소 피드백 UX 패턴과 일관.
- `CatBtn` 도 `disabled={busy}`(`DeviceSidebar.tsx:153`)로 busy 중 비활성.
  개별 `ActiveBtn` 은 disabled 미지정이나 클릭 시 `useSetActive` 뮤테이션이 큐잉되며 busy
  전파로 카테고리/전체 버튼이 잠기므로 상호 충돌 위험 낮음(경미 — 6절 후속 참조).

### 5) 범위 준수 / 의존성 — PASS (주의 1건, 비차단)
- Loop 07-B 실질 변경은 대상 3파일에 국한(`git diff --stat`: deviceService.ts +14,
  hooks.ts +18, DeviceSidebar.tsx +93). 신규 파일 없음.
- 수정 금지 파일(package.json, vite.config, tsconfig, .env) 변경 없음. 신규 의존성 없음.
- 주의: 프론트 repo `git status` 에 본 Loop 와 무관한 다수 변경 파일(admin TS 변환 등
  선행 커밋 계열)이 함께 존재하나, 이는 07-B 산출물이 아님. 07-B 검증 범위(viewer 3파일)
  내에서는 규칙 위반 없음. (오케스트레이터에 커밋 분리 인지만 권고 — 코드 결함 아님.)
- 미사용 `patchDeviceActive`(단건 `PATCH /device/{id}`) export 유지: 전 src grep 결과
  호출부 0(정의만 존재). 승인 요청서 2절(개별 `PATCH /{id}` 모달 전체편집용 유지) 결정에
  부합. tsc 상 미사용 export 는 오류 아님. **무해**. 완전 제거는 후속 Loop 권장.

### 6) 빌드 / 타입 — PASS
- `npx tsc --noEmit` → EXIT=0 (오류 없음).
- `npx vite build` → 성공(825 modules, built in 1.30s). chunk-size(>500kB) 경고는 전역
  기존 이슈로 본 변경과 무관. 작업 보고서 PASS 보고 재확인 완료.

---

## 2. 문서-코드 정합성
- 작업 보고서 2절(변경 파일·내용), 4절(계약 소비), 5절(patchDeviceActive 유지)이 실제
  코드와 일치.
- 07-A QA 3절 확정 계약(경로/body/응답/optional 규칙)과 프론트 구현 일치.
- 승인 요청서 26-29행(patchDeviceActiveBulk, 개별→scope=DEVICES 이관, CATEGORY/ALL 버튼,
  `['devices','all']` 무효화) 모두 반영됨.

---

## 3. Loop 07 종료 가능 여부: 가능
- 07-A(백엔드) PASS + 07-B(프론트) PASS. 계약 양단 일치, 회귀 없음, 빌드/타입 통과.
- 수정 요청 대상 AGENT: 없음. 재검증 필요: 없음.

---

## 4. 후속 항목 (비차단, WARNING 아님)
1. 미사용 단건 정리: 프론트 `patchDeviceActive`(및 백엔드 `PATCH /api/device/{id}` 의
   active-only 경로)는 모달 전체편집 도입 전까지 호출부 0. 편집 모달 도입 시 재사용하거나,
   미도입 확정 시 후속 Loop 에서 승인 후 제거 권장(현 상태 무해).
2. 카테고리 대량 DEVICES 폴백 요청 크기: 상위(대/중분류) 노드 토글 시 하위 전체 deviceId 를
   단일 요청 body 로 전송. 장비 수가 매우 많은 환경에서 요청 페이로드/백엔드 `IN` 절 크기
   증가 가능. 코드 결함 아님이나 대규모 배포 시 상한/청크 분할 검토 여지(설계 인지 사항).
3. 개별 `ActiveBtn` disabled 미적용: busy 중 카테고리/전체 버튼은 잠기나 개별 활성 버튼은
   연타 가능(뮤테이션 큐잉). 실사용 영향 경미하나 UX 일관성 위해 `disabled={busy}` 추가
   고려(선택).

---

## 5. 최종 결론
- 전체 판정: **PASS**
- 핵심 항목(계약 일치, 개별 토글 이관 무회귀, 카테고리 폴백·빈 목록 방어, ALL/busy 처리,
  tsc/build) 모두 PASS.
- 수정 요청 대상 AGENT: 없음.
- 재검증 필요: 없음.
- **Loop 07 종료 가능.**
