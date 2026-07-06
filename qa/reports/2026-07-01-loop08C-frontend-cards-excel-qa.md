# QA 검증 보고서 — Loop 08-C (프론트) 포인트 매핑 통계 카드 + 엑셀 양식/업로드

- 작성일: 2026-07-01
- 검증 AGENT: qa-code-reviewer
- Loop: 08-C (프론트 위성 `05-06.hanadream_web_new`)
- 근거: `qa/change-requests/2026-07-01-loop08-point-mapping-excel.md`, `docs/reports/2026-07-01-loop08C-frontend-cards-excel-report.md`
- 대상 저장소: `d:\HDD\05-06.hanadream_web_new`
- 검증 대상 파일(2건):
  - `src/services/deviceService.ts`
  - `src/pages/device/DevicePointMappingPage.tsx`

## 전체 판정: PASS

기존 UI/로직 무변경 제약을 diff로 확인했으며, 백엔드 08-B 계약과 3함수 경로·파라미터·
응답 언랩이 정확히 일치한다. 카드 산식·다운로드·업로드 처리 모두 요구사항 충족.
`tsc --noEmit` 전체 프로젝트 무오류(exit 0). Loop 08 종료 가능(경미 후속 항목 2건 권고).

---

## 항목별 검증 결과

### 1. 계약 일치 — PASS
- base URL: `src/services/api.ts:10` `VITE_API_BASE_URL || '/api'` → 최종 경로 `/api/device/points/mapping/...` 일치.
- `fetchPointMappingStatsAPI` (`deviceService.ts:321-332`): GET `/device/points/mapping/stats`,
  params `categoryId`(null 가드)/`keyword`(빈값 가드), 응답 `data?.data` 언랩 → `{total, mapped}`.
  백엔드 `DeviceController.java:210-217` + `PointMappingStatsResponse(total, mapped)` 일치.
- `downloadPointMappingTemplateAPI` (`deviceService.ts:339-351`): GET `/device/points/mapping/template`,
  `responseType: "blob"`, axios response 원본 반환(헤더 파싱용). 백엔드 `DeviceController.java:163-177`
  (Content-Disposition `attachment; filename=...; filename*=UTF-8''...`, MIME xlsx) 일치.
- `importPointMappingAPI` (`deviceService.ts:358-365`): POST `/device/points/mapping/import`,
  `FormData` field `file`, `multipart/form-data`, 응답 `data?.data` 언랩 →
  `{total, applied, failed[]}`. 백엔드 `DeviceController.java:188-196` `@RequestParam("file")` +
  `PointMappingImportResult(total, applied, failed:[{row, pointKey, reason}])` 필드명 정확 일치.
- ApiResponse 래핑: 백엔드 `ApiResponse.success(message, data)` → `data.data` 언랩 정상.

### 2. 카드 산식 — PASS
- `DevicePointMappingPage.tsx:99-102`:
  - 전체 = `statTotal`(total), 완료 = `statMapped`(mapped).
  - 미매핑 = `Math.max(0, statTotal - statMapped)` → 음수 방어 확인.
  - 매핑률 = `statTotal > 0 ? Math.round(statMapped/statTotal*100) : 0` → total=0 시 0% 확인.
- stats 쿼리(`:86-97`)는 `appliedCategoryId`, `keyword`만 키/파라미터로 사용 → 검색 필터 반영.
  `unmappedOnly` 토글은 목록 쿼리에만 반영되고 stats 쿼리 키에 없음 → **토글과 독립**(별도 조회) 확인.
- `dirtyRows.length > 0` 시 "변경 대기" 카드 추가(`:320-325`) — 기존 Summary의 변경 건수 정보 보존,
  정보 손실 없음.

### 3. 다운로드 — PASS (`:108-139`)
- blob → `URL.createObjectURL` → `a[download]` → click → remove → `revokeObjectURL` 호출 확인(메모리 누수 방지).
- Content-Disposition 파싱: `filename*=UTF-8''`(우선, `decodeURIComponent`) 및 `filename="..."` 폴백,
  헤더 미제공 시 `point-mapping-template.xlsx` 기본값(`:120`). 대소문자 헤더 키 양쪽 조회.
- 다운로드 중 `isDownloading` 가드 + 버튼 disabled(`:109, :274`), finally에서 상태 복구.
- 실패 시 alert 처리(`:134-135`).

### 4. 업로드 — PASS (`:141-175, :279-292`)
- 숨김 `<input type="file" accept=".xlsx">`(`HiddenFileInput`, `:480-482`), `handlePickFile`로 click 트리거.
- 파일 선택 즉시 `e.target.value = ""`(`:148`) → 동일 파일 재선택 허용 확인.
- `importPointMappingAPI` 호출 후 `DEVICE_POINT_MAPPING_QUERY_KEY` + `POINT_MAPPING_STATS_QUERY_KEY`
  invalidate(`:156-157`) → 테이블·카드 자동 갱신. `setDrafts({})`로 편집 상태 초기화.
- 결과 표시: `반영 applied/total건` + 실패 시 상위 10건(`row행 [pointKey] reason`) + 초과분 요약(`:160-169`).
- 업로드 중 `isImporting` 가드 + 버튼 disabled(`:142, :282`), finally 복구, 실패 시 alert.

### 5. 기존 UI/로직 무변경 (사용자 강조 제약) — PASS
`git diff` 2파일 분석 결과, 기존 로직은 전부 무변경. 변경은 순수 추가/치환에 국한:
- `deviceService.ts`: 신규 3함수 + 쿼리키 1개만 추가(diff 순수 additive), 기존 함수 무변경.
- `DevicePointMappingPage.tsx` 변경 범위:
  - import 추가(`useRef`, 신규 4심볼).
  - 목록 useQuery에서 미사용된 `mappedCount` 구조분해만 제거(select 내 `mappedCount` 자체는 유지) — 로직 영향 없음.
  - 신규 stats useQuery / 다운로드·업로드 핸들러 / 카드 그리드 추가.
  - 툴바: 텍스트 `Summary` → `ToolbarSpacer` + 다운로드/업로드 버튼으로 치환. `변경 저장` 버튼은
    `marginLeft:"auto"` 제거(Spacer가 대체) 외 동작 동일.
  - styled: `Summary` 제거, `ToolbarSpacer/HiddenFileInput/CardGrid/Card/CardLabel/CardValue` 추가.
- 무변경 확인: `DeviceHierarchyFilter`·검색 input/버튼·`미매핑만` 토글(`:244-269`), 논리 포인트 테이블
  (컬럼/행/상태 배지, `:333-420`), 인라인 `RefInput` + `drafts`/`updateDraft`/`isRowDirty`(`:177-211`),
  저장 mutation `saveDevicePointRefMappingAPI`/`변경 저장` 버튼(`:213-229, :293-300`), 페이지네이션·힌트박스.
  **회귀 없음.**

### 6. 범위 준수 — PASS
- 08-C 대상 2파일만 본 Loop 산출물로 확인.
- 수정 금지 파일 무변경: `git diff package.json package-lock.json tsconfig.json vite.config.ts` 결과 없음.
  신규 프론트 의존성 없음(백엔드가 xlsx 생성/파싱 담당).

### 7. 빌드/타입 — PASS
- `npx tsc --noEmit`: exit 0, 오류 0(전체 프로젝트, 미커밋 무관 파일 포함).
- (참고) 작업 보고서의 `npm run build` PASS 기록 확인. QA에서는 `tsc --noEmit`으로 타입 무결성 재확인.

---

## WARNING/후속 항목 (판정 무영향, 권고)

1. [범위 위생 · 권고] 프론트 작업트리에 본 Loop 무관 미커밋 변경 다수 공존:
   `src/components/modal/sop/SopTemplateCreateForm.tsx`, `src/components/sop/SopTemplatePreview.tsx`,
   `src/pages/event/EventThresholdPage.tsx`, `src/pages/sop/SopTemplateEditorPage.tsx`,
   `src/pages/sop/SopTemplateManagePage.tsx`, `?? src/constants/assetType.ts`,
   `?? src/pages/zone/SpaceLightManagePage.tsx`.
   → 08-C 2파일 외는 본 판정 대상 아님. 커밋 분리(08-C 2파일 별도 커밋) 권고.
2. [설계상 정상 · 참고] stats 카드는 `categoryId`(+`keyword`) 기준으로만 집계한다. 필터에서
   특정 장비(`deviceId`)를 선택하면 `resolveHierarchyCategoryId`가 `undefined`를 반환하여
   (`deviceHierarchyFilterUtils.ts:16`) 카드 값이 장비 범위로 좁혀지지 않는다. 이는 백엔드
   `stats` 계약에 `deviceId` 파라미터가 없는 것과 일치하는 의도된 동작이나, 목록(테이블)과
   카드의 집계 범위가 장비 선택 시 달라질 수 있음을 UX 관점에서 후속 확인 권고.
3. [경고 · 참고] `deviceService.ts` CRLF→LF 경고는 라인엔딩 정규화로 로직 무관.

---

## Loop 08 종료 가능 여부

**종료 가능(PASS).** 08-A(POI 의존성) → 08-B(백엔드 엔드포인트) → 08-C(프론트) 계약이
경로·파라미터·DTO 필드명 수준까지 일치하며, 기존 UI/로직 무변경 제약을 충족했다.
위 후속 항목은 위험도 낮음(범위 위생/UX 확인)으로, 커밋 분리 후 다음 Loop로 진행 가능.

## 재검증 필요 여부
불필요. 코드 수정 발생 시(후속 항목 반영 등)에 한해 해당 부분 재검증.
