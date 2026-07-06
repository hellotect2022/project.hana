# 코드 수정 승인 요청서

## 1. 수정 필요 여부
필요

## 2. 문제 요약
기획서("장비 에셋 관리")는 에셋 목록에 **타입 + 대/중/소 분류**를 표시한다. 그러나
현재 데이터 모델은:
- `UnityAsset`(tbl_unity_3d_asset)에 **타입·카테고리 컬럼이 없다.**
- 대/중/소(카테고리)는 **Device에 종속**(`Device.category`)이고, 에셋은 `Device.unityAsset`
  으로 **여러 device가 한 에셋을 참조(N:1)** 한다.
- 따라서 "한 에셋"의 대/중/소는 **단일 값으로 결정되지 않는다(1:N 다중값).** 한 에셋이
  서로 다른 카테고리의 device에 매핑될 수 있다(사용자 확인).

또한 "3D Asset 관리" 명칭을 "장비 에셋 관리"로 변경 요청.

## 3. 영향 범위
- server(hana-digitaltwin: unityAsset, 일부 device 읽기 DTO)
- database(tbl_unity_3d_asset 스키마 변경 / 마이그레이션)
- client(05-06: unityAsset 화면·서비스·폼, 메뉴 라벨)
- integration(백엔드 enum ↔ 프론트 표시 계약)

## 4. 수정 대상 파일 (예정)
### 백엔드 (spring-backend-agent)
- (신규) `unityAsset/entity/AssetType.java` — enum
- `unityAsset/entity/UnityAsset.java` — `assetType` 필드 추가
- `unityAsset/dto/UnityAssetRequest.java` — `assetType` 입력
- `unityAsset/dto/UnityAssetResponse.java` — `assetType`(영문 코드) + `assetTypeLabel`(한글)
- `unityAsset/service/UnityAssetService.java` — 생성/수정 시 assetType 반영, 정렬
- (선택, 방안 C) `unityAsset/...` — 에셋별 "사용 카테고리" 파생 조회 추가
- 명칭: 컨트롤러/서비스의 "3D 에셋" 메시지 문구 정리(선택)
### DB (database-agent)
- (신규) `db/migration/V3__add_asset_type_to_unity_3d_asset.sql`
  - `ALTER TABLE tbl_unity_3d_asset ADD COLUMN asset_type VARCHAR(32) NOT NULL DEFAULT 'EQUIPMENT';`
  - (검토 필요) ddl-auto 설정과 Flyway 동시 사용 여부 — devops-agent 확인
### 프론트 (react-frontend-agent, 별도 저장소)
- `constants/menuData.tsx` — "3D Asset 관리" → "장비 에셋 관리", path 검토
- `pages/unityAsset/UnityAssetManagePage.tsx` — 탭/타이틀 라벨
- `pages/unityAsset/AssetManageTab.tsx` — 컬럼에 **타입** 추가, 라벨 한글화
- `components/modal/unityAsset/UnityAssetManageForm.tsx` — assetType 선택 드롭다운
- `services/unityAssetService.ts` — payload/타입 주석에 assetType 반영
- (신규) assetType 코드→한글 매핑 상수 또는 응답의 `assetTypeLabel` 사용

## 5. 수정 사유
- 에셋 타입을 enum으로 표준화(영문 저장/한글 표시)하여 신규 타입(화장실 칸·주차 면 등)을
  일관되게 등록·시각화.
- 대/중/소를 에셋에 비정규화하면 1:N 다중값 충돌·데이터 무결성 위반이 발생하므로,
  구조에 맞는 안전한 표현 방식을 채택.

## 6. 수정 방향
### (확정) assetType enum
- `AssetType { EQUIPMENT("장비"), EFFECT("이펙트"), ZONE("구역"),
  EVACUATION_ROUTE("피난대피로"), TOILET_STALL("화장실 칸"), PARKING_SLOT("주차 면") }`
- 저장: `@Enumerated(EnumType.STRING)` → DB에 **영문 enum name** 저장.
- 표시: 응답 DTO에 `assetType`(영문 코드, 폼 value용) + `assetTypeLabel`(한글, 표시용) 동시
  제공. (코드 컨벤션 `DeviceCategoryDepth`의 code+label 패턴 준수)

### (선택 필요) 대/중/소 분류 표현 — 아래 3안 중 택1
- **방안 A (권장, MVP):** 에셋 목록에서 대/중/소 컬럼 **제외**. 에셋 그리드 = ID·에셋명·
  **타입**·설명·활성·관리. 대/중/소는 기존대로 device 레벨에서만 관리. → 무결성 안전, 변경
  최소, 기획서 의도(타입 구분)는 충족.
- **방안 C (확장):** 에셋별로 "사용 중 카테고리" 목록을 **읽기 전용 파생값**으로 표시
  (1:N 뱃지). 비정규화 없이 device에서 집계. 추가 조회 1건 필요.
- **방안 B (비권장):** "1 에셋 = 1 소분류" 강제(카테고리에 defaultAsset). 코드 *의도*와는
  맞지만 **현재 실데이터(한 에셋이 여러 카테고리 사용)와 충돌** → 데이터 마이그레이션·제약
  추가 필요, 리스크 큼.

## 7. 예상 변경 내용
- 추가: AssetType enum, asset_type 컬럼, DTO 필드(assetType/assetTypeLabel), 프론트 타입
  컬럼·드롭다운, 메뉴/타이틀 라벨.
- 변경: UnityAsset 엔티티/서비스, 명칭 문구.
- 삭제: (방안 A 채택 시) 기획서의 대/중/소 컬럼은 에셋 그리드에 추가하지 않음.

## 8. 위험도
- assetType enum 추가: **LOW** (가산 변경, 기본값 EQUIPMENT로 기존 데이터 호환)
- 대/중/소 방안 B 선택 시: **HIGH** (데이터 마이그레이션·제약)
- 방안 A/C 선택 시: **LOW**

## 9. 수정하지 않을 경우 문제
- 에셋 타입 구분 불가 → 신규 타입(화장실 칸·주차 면) 3D 배치/상태 시각화 분기 불가.
- 기획서대로 대/중/소를 에셋에 그대로 박으면 다중 카테고리 에셋에서 잘못된 분류 표시·
  데이터 불일치 발생.

## 10. 승인 요청
- assetType enum(영문 저장/한글 표시) 도입 및 "장비 에셋 관리" 명칭 변경: **승인/보류**
- 대/중/소 분류 처리: **방안 A / C / B 중 택1**

승인 후 database-agent → spring-backend-agent → react-frontend-agent 순으로
자기 소유 영역만 수정하고, 각 단계 작업 보고서 + QA 재검증을 진행한다.
