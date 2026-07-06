# QA 검증 보고서 — Loop 07-A 디바이스 active 일괄 변경 bulk 엔드포인트

- 작성일: 2026-07-01
- 검증자: qa-code-reviewer
- 대상 Loop: 07-A (spring-backend / hana-digitaltwin / device)
- 근거 문서:
  - 승인 요청서 `qa/change-requests/2026-07-01-loop07-device-active-bulk.md`
  - 작업 보고서 `docs/reports/2026-07-01-loop07A-device-active-bulk-report.md`
- 검증 대상 파일:
  - 신규 `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/device/dto/BulkActiveRequest.java`
  - 수정 `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/device/repository/DeviceRepository.java`
  - 수정 `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/device/service/DeviceService.java`
  - 수정 `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/device/api/DeviceController.java`

## 전체 판정: PASS

07-B(프론트) 진행 가능. 수정 요청 대상 AGENT 없음. 재검증 불필요.
후속 확인 항목(비차단) 2건은 아래 8절 참조.

---

## 1. 항목별 검증 결과

### 1) updatedAt 세팅 / @PreUpdate 우회 대응 (최중요) — PASS

- bulk `@Modifying` UPDATE 3종 모두 `d.updatedAt = :now` 를 SET 절에 포함
  (`DeviceRepository.java:124`, `:128`, `:134`).
- 엔티티 필드 경로 정확:
  - `d.category.categoryId` — `Device.category`(`Device.java:44`) → `DeviceCategory.categoryId`, JPQL 경로 유효.
  - `d.deviceId` — `Device.java:30` 존재.
  - `d.updatedAt` — `DateEntity.updatedAt`(`DateEntity.java:18`) 상속 필드, JPQL 접근 유효.
  - `d.active` — `Device.java:38` 존재.
- @PreUpdate 우회 대응 실효성: `DateEntity.@PreUpdate onUpdate()`(`DateEntity.java:26-29`)는
  엔티티 라이프사이클 콜백이라 bulk JPQL UPDATE 에서 실행되지 않는다. 서비스가
  `OffsetDateTime.now()`를 한 번 계산(`DeviceService.java:392`)해 세 쿼리에 `:now`로
  주입하므로 `updated_at`이 실제 갱신된다. 대응 유효.
- `clearAutomatically = true`: 세 쿼리 모두 명시됨(`:123`, `:127`, `:133`). bulk 후 1차 캐시
  clear 로 stale 엔티티 방지. 적절.

### 2) scope 분기 정확성 — PASS

- `ALL` → `updateActiveAll` (WHERE 없음, 전체 대상). 의도된 동작(`DeviceService.java:395`).
- `CATEGORY` → `resolveSmallCategoryIdsForFilter(categoryId)`로 소분류 ids 전개 후
  `updateActiveByCategoryIds(ids, ...)` (`WHERE d.category.categoryId IN :ids`). 매핑 정확
  (`DeviceService.java:400-404`, `DeviceRepository.java:128`).
- `DEVICES` → `updateActiveByDeviceIds(deviceIds, ...)` (`WHERE d.deviceId IN :ids`). 매핑 정확
  (`DeviceService.java:411`, `DeviceRepository.java:134`).
- 빈 ids 시 전체 UPDATE 누출 없음 (아래 항목 3에서 상세) — WHERE 절 있는 쿼리만 IN 사용,
  ids 비었을 땐 쿼리 호출 자체를 하지 않음.

### 3) 빈 ids / 누락 입력이 전체 UPDATE 로 새는 위험 — PASS (핵심 판정)

이 항목을 중점 검증했다. 결론: **전체 UPDATE 누출 없음.**

- `CATEGORY`:
  - `categoryId == null` → `IllegalArgumentException`(`DeviceService.java:397-399`). WHERE 없는
    `updateActiveAll` 로 절대 흐르지 않음.
  - `resolveSmallCategoryIdsForFilter` 반환 특성 확인
    (`DeviceCategoryCascadeService.java:117-127`):
    - `rootCategoryId == null` → `null` 반환.
    - 존재하지 않는 categoryId → `List.of()`(빈 리스트) 반환.
  - 서비스에서 `ids == null || ids.isEmpty()` → `yield 0`(`DeviceService.java:401-403`).
    즉 매칭 소분류가 없거나 존재하지 않는 카테고리면 **UPDATE 쿼리를 아예 호출하지 않고
    0을 반환**. 전체 UPDATE 누출 경로 없음.
- `DEVICES`:
  - `deviceIds == null || deviceIds.isEmpty()` → `IllegalArgumentException`
    (`DeviceService.java:407-410`). 빈 목록으로 `IN ()` 실행되거나 전체로 새는 경로 없음.
- `ALL` 만이 유일하게 WHERE 없는 전체 UPDATE 이며, 이는 명시적으로 scope=ALL 을 요청한
  경우에만 도달. 다른 scope 의 잘못된 입력이 ALL 쿼리로 흐르는 코드 경로는 존재하지 않음
  (switch 문 각 case 가 독립적으로 자기 쿼리만 호출).

### 4) 방어 검증 (active null / scope null / 범위별 필수값) — PASS

- `request == null || scope == null` → `IllegalArgumentException`(`DeviceService.java:384-386`).
- `active == null` → `IllegalArgumentException`(`DeviceService.java:387-389`).
- `CATEGORY + categoryId null` → `IllegalArgumentException`(`DeviceService.java:397-399`).
- `DEVICES + deviceIds null/빈` → `IllegalArgumentException`(`DeviceService.java:407-410`).
- 예외 → HTTP 상태 매핑: 네 케이스 모두 `IllegalArgumentException` 이며
  `GlobalExceptionHandler.handleIllegalArgumentException`(`GlobalExceptionHandler.java:61-67`)가
  `ResponseEntity.badRequest()`(HTTP 400) + `ApiResponse.error(msg, "INVALID_REQUEST")` 로 응답.
  요구된 400 계열 처리 충족.

### 5) 경로 매핑 충돌 (/active vs /{deviceId}) — PASS

- 신규 `@PatchMapping("/active")`(`DeviceController.java:205`) vs 기존
  `@PatchMapping("/{deviceId}")`(`DeviceController.java:169`).
- 전 프로젝트 grep 결과 `/api/device/active` 정의는 `DeviceController` 1곳뿐(중복 컨트롤러 없음).
- Spring MVC 는 리터럴 세그먼트(`/active`)를 경로 변수(`/{deviceId}`)보다 높은 우선순위로
  매칭하므로 런타임 라우팅 모호성 없음. 추가로 `deviceId`는 `Long` 이라 문자열 "active" 는
  타입 변환 후보에서도 배제. 두 매핑 공존 안전.
- 기존 `updateDevice` 로직/시그니처 무변경 → 회귀 없음.

### 6) 범위 준수 — PASS

- 변경 파일 4개 모두 `device` 도메인 하위(dto/repository/service/api). 타 도메인 무변경.
- `git status` 기준으로도 본 Loop 산출물은 device 하위 4파일에 국한.
- yml / build.gradle / SecurityConfig / 신규 의존성 변경 없음(작업 보고서 기술과 일치, 검증에서
  device 외 변경 미발견). 기존 `PATCH /{deviceId}` 유지 확인.

### 7) 컴파일 — PASS

- `./gradlew :hana-digitaltwin:compileJava -q` 실행 → **EXIT=0** (오류 없음).

### 8) 트랜잭션 / 영속성 — PASS

- `updateActiveBulk` 에 `@Transactional`(`DeviceService.java:382`) 적용. bulk UPDATE 원자성 보장.
- `clearAutomatically = true` 로 벌크 후 stale 방지(항목 1 참조).
- 반환값 `int`(updatedCount) → 컨트롤러가 `ApiResponse<Integer>` 로 응답
  (`DeviceController.java:206-208`). 명세(resp `ApiResponse<Integer>` updatedCount) 일치.

---

## 2. 문서-코드 정합성

- 승인 요청서 1절(수정 대상)과 실제 구현 일치: DTO 필드, 3종 @Modifying 쿼리(SET/WHERE),
  `resolveSmallCategoryIdsForFilter` 재활용, `PATCH /api/device/active` + `ApiResponse<Integer>`.
- 작업 보고서 4~6절의 엔드포인트/scope 동작/프론트 계약이 코드와 일치.
- API 명세(요청 body scope/categoryId/deviceIds/active, 응답 updatedCount) 구현 일치.

---

## 3. 07-B 프론트 진행 가능 여부: 가능

계약 확정(변경 없음):

- `PATCH /api/device/active`
- 요청: `{ scope: "ALL"|"CATEGORY"|"DEVICES", categoryId?: number, deviceIds?: number[], active: boolean }`
  - `active` 필수. `CATEGORY`면 `categoryId` 필수, `DEVICES`면 `deviceIds`(비어있지 않은 배열) 필수.
- 응답: `ApiResponse<number>`, `data` = updatedCount, `message` = `"{n}건 활성상태 변경"`.
- 오류: scope/active 누락, 범위별 필수값 누락 → HTTP 400 `INVALID_REQUEST`.
- `CATEGORY` 매칭 소분류 없음 / 존재하지 않는 categoryId → HTTP 200 + `data: 0` (예외 아님).
  프론트는 200/`data:0` 을 "변경 대상 없음"으로 구분 처리 권장.

---

## 4. 후속 확인 항목 (비차단, WARNING 아님)

1. 런타임 매핑 확인(정적 검증상 문제 없음): 애플리케이션 컨텍스트 기동 시 `/active` vs
   `/{deviceId}` 실제 라우팅을 통합 스모크에서 1회 확인 권장. (정적으로는 충돌 없음 확정.)
2. `scope=ALL` 대량 UPDATE 규모 인지: 전체 장비 대상 단일 트랜잭션이라 원자적으로 안전하나,
   대상 행 수가 큰 환경에서는 잠금/성능 영향을 운영 관점에서 인지 필요(코드 결함 아님, 설계상
   의도된 동작).

---

## 5. 최종 결론

- 전체 판정: **PASS**
- 최중요 항목(updatedAt bulk 세팅, 빈 ids/누락 입력 전체 UPDATE 누출 위험) 모두 PASS.
- 수정 요청 대상 AGENT: 없음.
- 재검증 필요: 없음.
- 다음: 07-B(react-frontend-agent) 진행 가능.
