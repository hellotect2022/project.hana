# 작업 보고서 — Loop 07-B 프론트 디바이스 active 일괄 처리 통합

- 작성일: 2026-07-01
- Loop: 07-B
- AGENT: react-frontend-agent
- 대상 저장소: `05-06.hanadream_web_new` (프론트 위성)
- 승인 요청서: `qa/change-requests/2026-07-01-loop07-device-active-bulk.md`
- 선행: 07-A(백엔드) `PATCH /api/device/active` bulk 엔드포인트 추가 완료

## 1. 변경 요약
디바이스 `active`(3D 표시/숨김) 변경을 **전체(ALL)·카테고리(CATEGORY)·개별(DEVICES)**
모두 단일 bulk 엔드포인트(`PATCH /api/device/active`)로 통일했다.

- **API 클라이언트**: `patchDeviceActiveBulk(body)` 추가 (`ApiResponse<number>` = updatedCount 반환).
- **개별 토글 이관**: 기존 단건 `PATCH /device/{id}`(`patchDeviceActive`) 대신
  개별 활성 토글이 `patchDeviceActiveBulk({scope:'DEVICES', deviceIds:[id], active})` 로 흐르도록
  `useSetActive` 훅 내부 구현을 bulk 로 교체(호출부 시그니처·캐시 무효화 결과 동일).
- **카테고리 행 토글 추가**: 카테고리 트리 각 행에 `표시/숨김` 버튼. 하위 디바이스가
  단일 categoryId 로 묶이면 `scope:'CATEGORY'`(백엔드가 하위 소분류 cascade),
  여러 categoryId 가 섞인 상위 노드(대/중분류 등)면 하위 deviceId 를 모아 `scope:'DEVICES'` 폴백.
- **전체 표시/전체 숨김 버튼 추가**: 사이드바 헤더(검색창 아래) BulkBar 에 배치, `scope:'ALL'`.
- **캐시 무효화**: 성공 시 기존 키 `['devices','all']` invalidate → 트리·3D 씬 자동 갱신.
- 로딩(mutation pending) 중 모든 버튼 `disabled`, 실패 시 `alert` 최소 피드백(기존 패턴).

## 2. 수정/생성 파일 목록
모두 `05-06.hanadream_web_new/src` 하위. 신규 파일 없음(기존 파일 수정만).

| 파일 | 변경 내용 |
|---|---|
| `src/viewer/data/deviceService.ts` | `BulkActiveRequest` 타입 + `patchDeviceActiveBulk()` 추가. `patchDeviceActive` 주석을 "모달 전체편집용"으로 갱신(로직 유지). |
| `src/viewer/hooks.ts` | `useSetActiveBulk()` 신규. `useSetActive()` 내부를 bulk(scope=DEVICES)로 교체. import 를 `patchDeviceActiveBulk`/`BulkActiveRequest`로 변경. |
| `src/viewer/ui/DeviceSidebar.tsx` | `collectDevices`/`singleCategoryId` 헬퍼, `countDevices`에 active 집계 추가. `CatBtn`/`BulkBar` 스타일. NodeView 에 카테고리 토글 버튼·busy·onToggleCategory props. DeviceSidebar 에 `useSetActiveBulk`·onToggleCategory·onToggleAll·전체 표시/숨김 BulkBar 추가. |

## 3. UI 배치 요약
- **전체**: DeviceSidebar 헤더(검색 입력 바로 아래) `[전체 표시] [전체 숨김]` 2버튼 바.
- **카테고리**: 각 카테고리 트리 행 우측에 `표시/숨김` 버튼(하위 전체 활성이면 "숨김", 하나라도
  비활성이면 "표시"로 라벨/색 표시).
- **개별**: 기존 디바이스 카드의 `활성/비활성` 버튼 그대로 유지(내부 경로만 bulk 로 이관).

## 4. 백엔드 API 계약 영향
- **소비만** 함. 프론트에서 백엔드 코드/계약을 변경하지 않음.
- 사용 계약: `PATCH /api/device/active`, body `{scope, categoryId?, deviceIds?, active}`,
  resp `ApiResponse<number>`(updatedCount). 07-A 계약과 일치.
- CATEGORY scope 는 백엔드 `resolveSmallCategoryIdsForFilter(categoryId)` cascade 에 의존.
  상위 분류(다중 categoryId) 행은 프론트에서 DEVICES scope 로 안전 폴백하므로
  백엔드 cascade 미대응 상황에서도 정확히 동작.

## 5. 기존 단건 `patchDeviceActive` 정리 여부
- `patchDeviceActive`(단건 `PATCH /device/{id}`)는 **호출부가 사라졌으나 export 유지**(제거하지 않음).
- 이유: 승인 요청서(2절)에서 개별 `PATCH /api/device/{id}`를 **모달 전체편집용으로 유지**하기로
  결정됨. 현재 이 프론트 저장소에는 해당 편집 모달이 아직 없어 미사용 상태이나, 향후 모달이
  이 헬퍼를 재사용할 것을 고려해 남겨둠. tsc/build 상 미사용 export 는 오류 아님(검증 통과).
- 완전 삭제가 필요하면 후속 Loop 에서 별도 승인 후 제거 권장.

## 6. 다른 AGENT 영향
- **백엔드(spring-backend)**: 계약 소비만. 추가 변경 요청 없음.
- 개별 토글이 bulk(scope=DEVICES)로 이관됨 → 백엔드 `PATCH /device/{id}` 트래픽은
  모달 편집이 붙기 전까지 사실상 0 이 됨(엔드포인트 자체는 유지).

## 7. 검증
- `npx tsc --noEmit` → EXIT 0 (오류 없음).
- `npx vite build` → 성공(825 modules, built). chunk-size 경고는 기존 이슈로 이번 변경과 무관.
- 신규 의존성/설정(package.json·vite.config·tsconfig) 변경 없음.

## 8. QA 요청 항목
1. `patchDeviceActiveBulk` 요청 body 가 07-A 계약(scope enum·optional 필드)과 정확히 일치하는지.
2. 개별 토글 이관 후 동작/캐시 무효화 결과가 기존과 동일한지(3D 숨김/표시 반영).
3. 카테고리 행 CATEGORY↔DEVICES 폴백 분기(단일/다중 categoryId)의 타당성 및 대량 요청 시 안전성.
4. 미사용 `patchDeviceActive` export 유지 결정의 적정성(모달 편집 도입 시점까지 보류).
5. busy 중 버튼 disabled·실패 alert 최소 피드백이 기존 UX 패턴과 어긋나지 않는지.
