# 코드 수정 승인 요청서

- 작성일: 2026-07-01
- Loop: 07
- 작성: loop-orchestrator
- 관련: 디바이스 활성/비활성(active) 일괄 처리 — 전체/카테고리/개별 통합

## 0. 배경 / 결정 (사용자 승인)
- 디바이스 `active`(3D 표시/숨김) 변경을 **전체·카테고리·개별 모두 단일 bulk 엔드포인트로 통일**.
- 카테고리 토글 = 하위(소분류) 디바이스 active 일괄 변경(`DeviceCategory.enabled` 미변경).
- 기존 범용 `PATCH /api/device/{id}`(모달 전체편집)는 유지.
- **주의**: bulk `@Modifying` UPDATE는 `DateEntity.@PreUpdate`를 우회 → 쿼리에서 `updatedAt` 명시 세팅 필수.

## 1. 수정 대상
### 07-A (spring-backend, hana-digitaltwin/device)
- **신규** `dto/BulkActiveRequest.java` — `scope`(ALL|CATEGORY|DEVICES), `categoryId`(Long, nullable), `deviceIds`(List<Long>, nullable), `active`(Boolean).
- **신규** (선택) `dto/BulkActiveResponse.java` 또는 ApiResponse<Integer> — `updatedCount`.
- **수정** `repository/DeviceRepository.java` — `@Modifying(clearAutomatically=true)` 3종:
  - ALL: `UPDATE Device d SET d.active=:active, d.updatedAt=:now`
  - CATEGORY: `... WHERE d.category.categoryId IN :ids`
  - DEVICES: `... WHERE d.deviceId IN :ids`
- **수정** `service/DeviceService.java` — `updateActiveBulk(BulkActiveRequest)`:
  - scope별 분기, CATEGORY는 `DeviceCategoryCascadeService.resolveSmallCategoryIdsForFilter(categoryId)`로 하위 소분류 ids 해석 후 IN 업데이트. `OffsetDateTime.now()` 전달. 반환=updatedCount.
- **수정** `api/DeviceController.java` — `PATCH /api/device/active` 엔드포인트 1개(ApiResponse<updatedCount>). 기존 `/{deviceId}` 와 경로 충돌 없음(리터럴 우선) 확인.

### 07-B (react-frontend, 05-06/src)
- **신규/수정** viewer/data(또는 services) `patchDeviceActiveBulk(scope, {categoryId|deviceIds}, active)`.
- DeviceSidebar 개별 토글을 이 함수(scope=DEVICES,[id])로 이관 + 전체표시/전체숨김(ALL) + 카테고리 토글(CATEGORY) 버튼.
- 성공 시 `['devices','all']` 캐시 무효화(기존 useSetActive 패턴).

## 2. 재활용 포인트
- `DeviceCategoryCascadeService.resolveSmallCategoryIdsForFilter()` (카테고리 계층 → 소분류 ids).
- 기존 개별 `PATCH /api/device/{id}` 는 전체편집용으로 유지(제거 안 함).

## 3. 위험도
MEDIUM
- bulk JPQL UPDATE는 영속성 컨텍스트/`@PreUpdate` 우회 → `updatedAt` 수동 세팅, `clearAutomatically=true` 필수(QA 확인).
- 경로 매핑 `/api/device/active` vs `/{deviceId}` 우선순위 검증.
- 대량 UPDATE 트랜잭션(전체 scope) — 원자적이라 안전하나 대상 규모 인지.

## 4. 승인
사용자 승인 완료(2026-07-01). 07-A(백엔드) → QA → 07-B(프론트) → QA.
