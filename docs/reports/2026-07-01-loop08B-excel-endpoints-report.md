# Loop 08-B 작업 보고서 — 포인트 매핑 엑셀 다운로드/업로드 + 통계 엔드포인트

- 작성일: 2026-07-01
- 작성 AGENT: spring-backend-agent
- 대상 모듈: hana-digitaltwin (WebMVC + JPA)
- 승인 요청서: qa/change-requests/2026-07-01-loop08-point-mapping-excel.md
- 선행: Loop 08-A 에서 poi-ooxml:5.2.5 의존성 추가됨(본 작업은 신규 의존성 없음)

## 1. 작업 목표

포인트 매핑 관리 화면(프론트 08-C)이 소비할 3개 신규 엔드포인트를
`device` 도메인 하위에만 추가한다.

1. 엑셀 양식 다운로드 (현재 필터 범위 포인트 전체, ref 값 프리필)
2. 엑셀 업로드 (부분 반영 + 실패 목록 반환)
3. 매핑 통계 (total / mapped)

## 2. 변경 요약

- 기존 조회/수정 로직(`getDevicePointsForMappingPage`, `updateDevicePointRefMappings`,
  `findForRefMappingPage`, `countMappedForRefMapping`)은 **전혀 수정하지 않고 재활용(호출)만** 하였다.
- 엑셀 전용 로직은 신규 서비스 `DevicePointMappingExcelService` 로 분리하고,
  매핑 반영은 기존 `DeviceService.updateDevicePointRefMappings(List)` 를 그대로 호출한다.
- 리포지토리에는 기존 쿼리를 건드리지 않고 신규 조회/카운트 메서드만 추가하였다.
- yml / build.gradle / DB 스키마는 건드리지 않았다.

## 3. 변경 파일 목록

신규:
- `hana-digitaltwin/.../device/service/DevicePointMappingExcelService.java` (템플릿/업로드/통계 로직)
- `hana-digitaltwin/.../device/dto/PointMappingImportResult.java` (업로드 결과 DTO + 중첩 `FailedRow`)
- `hana-digitaltwin/.../device/dto/PointMappingStatsResponse.java` (통계 DTO)

수정(추가만, 기존 메서드 무변경):
- `hana-digitaltwin/.../device/api/DeviceController.java`
  - 3개 엔드포인트 추가, 필드/임포트 추가. 기존 엔드포인트 로직 무변경.
- `hana-digitaltwin/.../device/repository/DevicePointRepository.java`
  - 신규 메서드 4개 추가: `countForRefMapping`, `findAllForRefMapping`,
    `findByPointKey`, `findAllByPointKeyIn`. 기존 쿼리 무변경.

## 4. 엔드포인트 계약 (프론트 08-C 소비용)

공통 접두: `/api/device/points/mapping`

### 4.1 GET `/template?categoryId=&keyword=`
- 응답: `ResponseEntity<byte[]>` (blob)
  - Content-Type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  - Content-Disposition: `attachment; filename="point-mapping-template.xlsx"; filename*=UTF-8''...`
- 시트 1개("포인트매핑"), 헤더행:
  `장비명 | 태그 | 포인트명 | point_key | ref_device_code | ref_point_code`
- 데이터행: 현재 필터 범위 포인트 전체(페이징 없음), ref 컬럼은 기존값 프리필(없으면 빈칸).
- 프론트: `responseType: 'blob'` 로 받아 파일 저장.

### 4.2 POST `/import` (multipart/form-data, 파라미터명 `file`)
- 요청: `@RequestParam("file") MultipartFile` (xlsx)
- 응답: `ApiResponse<PointMappingImportResult>`
  - `PointMappingImportResult { int total; int applied; List<FailedRow> failed; }`
  - `FailedRow { int row; String pointKey; String reason; }` (row 는 1-based 엑셀 행번호)
- 파싱: 헤더명으로 컬럼 인덱스 해석(순서 비의존). 필수 헤더:
  `point_key`, `ref_device_code`, `ref_point_code` (누락 시 400 계열: IllegalArgumentException).
- 행 단위 검증(실패는 건너뛰고 사유 수집):
  - `point_key` 미입력 / 미존재 → 실패
  - both-or-neither 위반(한쪽만 입력) → 실패
  - 파일 내 ref 조합 중복 → 실패
  - 기존 DB 의 다른 포인트와 ref 중복 → 실패
    (`findByRefDeviceCodeAndRefPointCodeAndPointIdNot` 재활용)
- 유효행만 `DevicePointRefMappingRequest` 리스트로 만들어 기존
  `updateDevicePointRefMappings` 호출로 반영.

### 4.3 GET `/stats?categoryId=&keyword=`
- 응답: `ApiResponse<PointMappingStatsResponse>`
  - `PointMappingStatsResponse { long total; long mapped; }`
- `mapped` = 기존 `countMappedForRefMapping` 재활용,
  `total` = 신규 `countForRefMapping`(동일 WHERE, mapped 필터만 제거).
- 미매핑 토글과 무관하게 category/keyword 범위 기준.

## 5. 기술 판단 근거

- **기존 메서드 무변경(재활용만)**: 승인 범위상 기존 매핑 흐름 변경 금지. 전체 목록/총계가
  필요해 기존 페이지/카운트 메서드의 시그니처를 바꾸지 않고, 동일 WHERE 절을 갖는 신규
  리포지토리 메서드를 추가했다. 업로드 반영은 기존 `updateDevicePointRefMappings` 를 호출한다.
- **부분 반영 정책**: 유효행만 반영 + 실패 목록 반환. `@Transactional` 로 감싸며, 사전 검증
  (존재/both-or-neither/파일내·DB 중복)으로 기존 update 로직이 throw 하지 않도록 걸러낸다.
  사전 검증을 통과했음에도 경합 등으로 예외가 발생하면 부분 커밋을 남기지 않도록 예외를
  전파(트랜잭션 롤백)한다.
- **POI 리소스 처리**: `XSSFWorkbook` / `ByteArrayOutputStream` / 업로드 `InputStream` 모두
  try-with-resources 로 close. 다운로드는 워크북을 바이트 배열로 직렬화 후 반환하여 스트림
  누수를 방지한다.
- **파일명 인코딩**: RFC 5987 `filename*` 를 함께 제공(한글/특수문자 대비, 현재는 ASCII 파일명).
- **컬럼 순서 비의존**: 헤더명으로 인덱스를 찾아 `point_key`/`ref_*` 위치가 바뀌어도 동작.
  `DataFormatter` 로 셀 타입(숫자/문자) 무관하게 문자열로 안전 파싱.

## 6. 검증

- `./gradlew :hana-digitaltwin:compileJava -q` → 성공(오류 없음).
- 컴파일 산출물 확인: `DevicePointMappingExcelService.class`,
  `PointMappingImportResult(.class/$FailedRow.class)` 생성 확인.
- IDE 인덱스의 `org.apache.poi` 미해결 진단은 인덱서 클래스패스 문제로, gradle 컴파일에서는
  poi-ooxml:5.2.5 가 정상 해석되어 오류 없음.

## 7. QA 요청 항목

1. 기존 매핑 조회/수정 엔드포인트(`GET /points/mapping`, `PUT /points/ref-mapping`)
   회귀 없음 확인(로직 무변경).
2. 3개 신규 엔드포인트 계약(경로/응답 래퍼/멀티파트) 및 Swagger 노출 확인.
3. 업로드 부분 반영/검증 정책 및 트랜잭션 경계 적정성.
4. POI 리소스 close(try-with-resources) 처리 확인.
5. 프론트 08-C 가 소비할 계약(template blob / import multipart 결과 / stats) 명세 정합성.
