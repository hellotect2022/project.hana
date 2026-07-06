# Loop 07-A 작업 보고서 — 디바이스 active 일괄 변경 bulk 엔드포인트

- 작성일: 2026-07-01
- 담당: spring-backend-agent
- 승인 요청서: `qa/change-requests/2026-07-01-loop07-device-active-bulk.md`
- 대상 모듈/범위: `hana-digitaltwin` / `device` 도메인 하위 (엄격 준수)

## 1. 작업 개요

디바이스 활성상태(`active`)를 **전체(ALL) / 카테고리(CATEGORY) / 개별(DEVICES)** 세 가지
범위(scope)로 처리하는 통합 bulk PATCH 엔드포인트를 신규 추가했다. 기존 범용
`PATCH /api/device/{deviceId}`(`updateDevice`)는 변경 없이 그대로 유지했다.

## 2. 변경 요약

- 신규 요청 DTO `BulkActiveRequest`(record) 추가 — `scope`(중첩 enum ALL/CATEGORY/DEVICES),
  `categoryId`, `deviceIds`, `active` 필드.
- `DeviceRepository`에 `@Modifying` bulk UPDATE 3종 추가(ALL / 카테고리 IN / deviceId IN).
- `DeviceService.updateActiveBulk(BulkActiveRequest)` 추가 — scope 분기 + 방어적 검증.
- `DeviceController`에 `PATCH /api/device/active` 매핑 추가(Swagger @Operation 포함).

## 3. 변경/생성 파일 목록

| 구분 | 파일 |
|---|---|
| 생성 | `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/device/dto/BulkActiveRequest.java` |
| 수정 | `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/device/repository/DeviceRepository.java` |
| 수정 | `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/device/service/DeviceService.java` |
| 수정 | `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/device/api/DeviceController.java` |

yml / build.gradle / SecurityConfig / 타 도메인 / 신규 의존성 변경 없음.

## 4. 엔드포인트 명세

- `PATCH /api/device/active`
- 요청 Body (`BulkActiveRequest`):

```json
{
  "scope": "ALL | CATEGORY | DEVICES",
  "categoryId": 12,        // scope=CATEGORY 시 필수, 그 외 무시
  "deviceIds": [100, 101], // scope=DEVICES 시 필수, 그 외 무시
  "active": true           // 필수
}
```

- 응답 (`ApiResponse<Integer>`):

```json
{
  "success": true,
  "message": "3건 활성상태 변경",
  "data": 3,               // 변경된 장비 건수(updatedCount)
  "errorCode": null
}
```

### scope 별 동작

- `ALL` — 전체 장비 대상. `categoryId`/`deviceIds` 무시.
- `CATEGORY` — `categoryId` 필수. `DeviceCategoryCascadeService.resolveSmallCategoryIdsForFilter(categoryId)`
  로 선택 카테고리(대·중·소) 하위 소분류 id 목록을 구해 해당 소분류 장비만 변경.
  매칭 소분류가 없으면 `0` 반환(예외 아님).
- `DEVICES` — `deviceIds` 필수(null/빈 목록이면 400성 예외).

### 검증(방어적)

- `scope`가 null → `IllegalArgumentException`.
- `active`가 null → `IllegalArgumentException`.
- `scope=CATEGORY`인데 `categoryId` null → `IllegalArgumentException`.
- `scope=DEVICES`인데 `deviceIds` null/빈 목록 → `IllegalArgumentException`.

## 5. 기술 판단 근거

### (1) bulk가 @PreUpdate 를 우회 → updatedAt 수동 세팅으로 해결

`Device`는 `DateEntity`(`@MappedSuperclass`)를 상속하며 `updated_at`은 `@PreUpdate`
콜백으로만 갱신된다. JPA bulk `@Modifying` UPDATE 는 영속성 컨텍스트/엔티티 라이프사이클
콜백을 우회하므로 `@PreUpdate`가 실행되지 않는다. 따라서 세 bulk 쿼리 모두
`SET d.active = :active, d.updatedAt = :now` 로 `updatedAt`을 명시 세팅하고, 서비스에서
`OffsetDateTime.now()`를 한 번 계산해 주입한다. 또한 `clearAutomatically = true`로 벌크
업데이트 후 1차 캐시를 비워 이후 조회 시 stale 엔티티가 반환되지 않도록 했다.

### (2) 경로 매핑 충돌 없음 확인 결과

- 기존 `@PatchMapping("/{deviceId}")` vs 신규 `@PatchMapping("/active")`.
- Spring MVC 는 리터럴 경로(`/active`)가 경로 변수(`/{deviceId}`)보다 **높은 우선순위**로
  매칭되므로 `PATCH /api/device/active` 요청은 항상 `updateActiveBulk`로 라우팅된다.
- `deviceId`는 `Long`이며 `active`는 숫자로 변환 불가하므로 설령 우선순위가 없더라도
  타입 불일치로 `/{deviceId}` 매핑 후보에서 배제된다. 두 매핑 공존은 정상.
- `compileJava` 성공(오류 없음). 애플리케이션 컨텍스트 로딩(런타임 매핑 충돌)은 QA 통합
  검증에서 확인 권장.

### (3) CascadeService 재활용

카테고리 범위 해석은 기존 `resolveSmallCategoryIdsForFilter`(목록 조회 필터에서 이미
사용)를 그대로 재활용해 대·중·소 계층 → 소분류 id 전개 로직 일관성을 유지했다.
`DeviceService`에 이미 주입돼 있던 `deviceCategoryCascadeService`를 사용(신규 주입 없음).

## 6. 07-B 프론트 소비 계약 (Contract)

- 엔드포인트: `PATCH /api/device/active`
- 요청: `{ scope: "ALL" | "CATEGORY" | "DEVICES", categoryId?: number, deviceIds?: number[], active: boolean }`
  - `active` 필수. `scope=CATEGORY`면 `categoryId` 필수, `scope=DEVICES`면 `deviceIds` 필수.
- 응답: `ApiResponse<number>` — `data`가 변경된 장비 건수(`updatedCount`), `message`는 `"{n}건 활성상태 변경"`.
- 400성(IllegalArgumentException) 케이스: scope/active 누락, 범위별 필수 파라미터 누락.
- `CATEGORY` scope에서 매칭 소분류 없음 → HTTP 200 + `data: 0`.

## 7. QA 요청 항목

1. `compileJava` 통과 확인(완료). 애플리케이션 컨텍스트 기동 시 `/active` vs `/{deviceId}`
   매핑 충돌 없음 런타임 확인.
2. 세 scope 각각 정상 동작 및 `updated_at` 실제 갱신 여부(bulk @PreUpdate 우회 대응) 검증.
3. 방어적 검증 예외 4종이 기존 전역 예외 핸들러에서 400 계열로 응답되는지 확인.
4. `clearAutomatically=true` 로 벌크 후 재조회 시 stale 데이터 없는지 확인.
5. 기존 `updateDevice`(`PATCH /{deviceId}`) 회귀 없음 확인.
