# Loop 08-C 작업 보고서 — 포인트 매핑 통계 카드 + 엑셀 양식/업로드 (프론트)

- 작성일: 2026-07-01
- 담당 AGENT: react-frontend-agent (프론트 위성 `05-06.hanadream_web_new`)
- 승인 요청서: `qa/change-requests/2026-07-01-loop08-point-mapping-excel.md`
- 대상 저장소: `d:\HDD\05-06.hanadream_web_new` (src 하위만)

## 1. 작업 개요

백엔드(Loop 08-B)가 추가한 3개 엔드포인트를 소비하여 "포인트 정보 매핑" 화면에
(A) 상단 통계 카드, (B) 엑셀 양식 다운로드 / 파일 업로드 버튼을 추가했다.
기존 필터·테이블·인라인 ref 입력·저장(useMutation) 로직은 변경하지 않았다.

## 2. 변경/생성 파일 목록

수정 (2건, 모두 `05-06.hanadream_web_new/src` 하위):

- `src/services/deviceService.ts`
  - 신규 함수 3개 및 쿼리 키 1개 추가 (기존 함수 무변경):
    - `fetchPointMappingStatsAPI({categoryId?, keyword?})` → GET `/device/points/mapping/stats` → `{ total, mapped }`
    - `downloadPointMappingTemplateAPI({categoryId?, keyword?})` → GET `/device/points/mapping/template` (`responseType: "blob"`), axios response 반환(파일명 파싱용)
    - `importPointMappingAPI(file)` → POST `/device/points/mapping/import` (multipart, field `file`) → `{ total, applied, failed[] }`
    - `POINT_MAPPING_STATS_QUERY_KEY = ["device","points","mapping","stats"]`
  - base URL `/api` 확인 → 최종 경로 `/api/device/points/mapping/...` 일치.

- `src/pages/device/DevicePointMappingPage.tsx`
  - 상단 텍스트 Summary(총/매핑됨/미매핑 문구)를 통계 카드 그리드로 교체.
    - 카드 4개: 전체 포인트(total) / 매핑 완료(mapped) / 매핑률(mapped/total×100, total=0 → 0%) / 미매핑(total−mapped).
    - `dirtyRows.length > 0`이면 "변경 대기" 카드 1개를 추가로 표시(기존 변경 건수 정보 유지, 정보 손실 없음).
    - 데이터: `useQuery([...STATS_KEY, appliedCategoryId, keyword], fetchPointMappingStatsAPI)`.
      적용된 검색 필터(카테고리/검색어) 반영, `unmappedOnly` 토글과 무관하게 항상 4개 값 정확.
  - 툴바 우측에 버튼 2개 추가(기존 `Button` 컴포넌트 재사용):
    - 양식 다운로드: blob 수신 → `Content-Disposition` 파일명 파싱(실패 시 `point-mapping-template.xlsx`) → `a[download]` 저장 → object URL revoke. 다운로드 중 disabled.
    - 파일 업로드: 숨긴 `<input type="file" accept=".xlsx">` → `importPointMappingAPI` → 결과 alert(`반영 applied/total건`, 실패 시 상위 10건 목록) → 목록/통계 쿼리 invalidate로 테이블·카드 자동 갱신. 업로드 중 disabled.
  - 로컬 styled 추가: `ToolbarSpacer`, `HiddenFileInput`, `CardGrid`, `Card`, `CardLabel`, `CardValue`(SystemServerPage 패턴 참고, 공용 컴포넌트 신설 없음). 미사용 `Summary` styled 제거.
  - 미사용이 된 `mappedCount` 구조분해 제거(그 외 로직 무변경).

## 3. 기존 UI·로직 무변경 확인

- 대분류/중분류/장비 필터(`DeviceHierarchyFilter`), 검색어 입력·검색 버튼, `미매핑만` 토글: 무변경.
- 논리 포인트 테이블(컬럼·행 렌더·상태 배지), 인라인 `ref_device_code`/`ref_point_code` 입력·draft 관리: 무변경.
- 기존 저장 mutation(`saveDevicePointRefMappingAPI` / `변경 저장` 버튼): 무변경(버튼 위치만 툴바 우측 정렬 유지, 동작 동일).
- 페이지네이션·힌트박스: 무변경.

## 4. 백엔드 API 계약 영향

- 없음(소비 전용). 08-B 계약을 그대로 사용:
  - GET `/api/device/points/mapping/stats?categoryId=&keyword=` → `ApiResponse<{total,mapped}>`
  - GET `/api/device/points/mapping/template?categoryId=&keyword=` → xlsx blob (`Content-Disposition: attachment`)
  - POST `/api/device/points/mapping/import` (multipart `file`) → `ApiResponse<{total,applied,failed[]}>`
- 백엔드 저장소 코드 변경 없음.

## 5. 검증

- `npm run typecheck` (tsc --noEmit): PASS (에러 0).
- `npm run build` (vite build): PASS. (chunk-size 경고는 기존부터 존재, 이번 변경 무관.)
- 신규 의존성/설정(package.json/vite.config/tsconfig) 변경 없음.

## 6. 다른 AGENT 영향

- 백엔드 계약 소비만. 기존 필터/테이블/저장 로직 무변경.
- QA 요청 항목:
  1. 통계 카드 4값(전체/매핑/매핑률/미매핑) 산식 및 total=0 시 0% 처리.
  2. 검색 필터(카테고리/검색어) 변경 시 카드 갱신, `미매핑만` 토글과 카드 값 독립성.
  3. 양식 다운로드 파일명 파싱(정상/미제공 케이스) 및 object URL revoke.
  4. 업로드 성공/부분실패(failed 목록) 표시, 성공 후 목록·통계 쿼리 invalidate 갱신, 업로드 중 disabled.
  5. 기존 인라인 저장(`변경 저장`) 및 필터/테이블 회귀 없음.
