# 코드 수정 승인 요청서

- 작성일: 2026-07-01
- Loop: 08
- 작성: loop-orchestrator
- 관련: 포인트 정보 매핑 — 통계 카드 + 엑셀 양식 다운로드/파일 업로드(ref 코드 일괄 수정)

## 0. 배경 / 결정 (사용자 승인)
- 포인트 매핑 화면에 (A) 전체/완료/매핑률/미매핑 통계 카드, (B) 엑셀 양식 다운로드 + 파일 업로드(일괄 수정) 추가.
- **제약: 다른 기존 로직·UI 절대 변경 금지.** 필터·테이블·인라인 ref 입력·저장 로직 무변경.
- 엑셀 방식: **백엔드 Apache POI (.xlsx)** 확정.

## 1. 재활용 현황 (조사 결과)
- ref 코드 일괄 수정 API 이미 있음: `PUT /api/device/points/ref-mapping` + `DeviceService.updateDevicePointRefMappings()`(중복검사·트랜잭션). 업로드 내부에서 재활용.
- 통계 데이터: 조회 응답에 `mappedCount` + `totalElements` 존재. 단 미매핑 토글 시 의미 변동 → 카드 정확도 위해 stats 엔드포인트 추가.
- 엑셀 라이브러리: 백엔드/프론트 모두 없음 → 신규 의존성 필요.

## 2. 수정 대상
### 08-A (devops, 수정 금지 파일 — 승인 필수)
- `hana-digitaltwin/build.gradle` — `implementation 'org.apache.poi:poi-ooxml:5.2.5'` 추가 (Spring Boot 3.4/Java 21 호환).

### 08-B (spring-backend, hana-digitaltwin/device — 신규 위주, 기존 무변경)
- **신규 엔드포인트** (DeviceController 또는 신규 컨트롤러):
  - `GET /api/device/points/mapping/template?categoryId=&keyword=` → xlsx 생성/다운로드. 컬럼: 장비명, 태그(tag_name), 포인트명, point_key, ref_device_code, ref_point_code. 현재 범위 포인트 프리필.
  - `POST /api/device/points/mapping/import` (multipart) → POI 파싱 → point_key 매칭 → 검증 → `updateDevicePointRefMappings` 재활용 → 결과 `{total, applied, failed:[{row, pointKey, reason}]}`.
  - `GET /api/device/points/mapping/stats?categoryId=&keyword=` → `{total, mapped}` (카드용, count 쿼리 재활용).
- **신규 Service 메서드**: 엑셀 생성, 엑셀 파싱→업데이트, 통계 count. **기존 조회/수정 메서드는 변경 금지**(재활용만).
- **신규 DTO**: import 결과.
- Repository: point_key 목록 조회 메서드 필요 시 추가(기존 무변경).

### 08-C (react-frontend, 05-06/src — 지정 영역만)
- `DevicePointMappingPage.tsx`: 텍스트 Summary → **카드 4개**(전체/완료/매핑률/미매핑). 카드 데이터는 stats 엔드포인트 사용. **필터·테이블·인라인 입력·저장 로직 무변경.**
- **양식 다운로드 버튼**: GET template blob → .xlsx 다운로드.
- **파일 업로드 버튼**: `<input type=file>` → FormData multipart POST → 결과(반영/실패 건수) 표시 → 성공 시 목록·카드 refetch(캐시 무효화).
- `deviceService.ts`: template/import/stats 클라이언트 함수 추가. 신규 프론트 의존성 없음(백엔드가 xlsx 생성/파싱).

## 3. 위험도
MEDIUM
- POI 신규 의존성(빌드 크기↑, 버전 호환). CVE/버전은 5.2.5 안정판.
- 업로드 파싱 방어(빈 파일/형식 오류/미존재 point_key/중복 ref) — 검증 결과를 실패목록으로 반환, 부분 반영 정책 명확화(유효행만 반영 or 전건 롤백)는 08-B에서 확정(권장: 유효성 통과분만 반영 + 실패목록 리턴, 기존 update의 중복검사는 트랜잭션 내).
- 다른 UI/로직 불변 제약 — QA에서 회귀 확인.

## 4. 승인
사용자 승인 완료(2026-07-01). 08-A(devops) → 08-B(백엔드) → QA → 08-C(프론트) → QA.
