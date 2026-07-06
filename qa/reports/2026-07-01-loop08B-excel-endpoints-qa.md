# QA 검증 보고서 — Loop 08-B 포인트 매핑 엑셀 다운로드/업로드 + 통계 엔드포인트

- 작성일: 2026-07-01
- 작성 AGENT: qa-code-reviewer
- 대상 모듈: hana-digitaltwin / device
- 근거 문서:
  - 승인 요청서 `qa/change-requests/2026-07-01-loop08-point-mapping-excel.md`
  - 작업 보고서 `docs/reports/2026-07-01-loop08B-excel-endpoints-report.md`
- 검증 방식: 소스 정독 + git diff 확인 + `./gradlew :hana-digitaltwin:compileJava -q`

## 0. 전체 판정: WARNING

- 08-B 산출물(3 엔드포인트 / 신규 서비스 / DTO 2 / 리포지토리 4메서드) 자체는 **PASS 수준**.
  기존 로직 무변경·부분반영/롤백 정책 일관·컴파일 성공 확인.
- 다만 **스코프 위반 2건**(device 하위이나 08-B 범위 밖 파일이 working tree 에 함께 변경됨:
  `DeviceCategoryCascadeService.java`, `DeviceLocationResponse.java`)이 발견되어 전체
  WARNING 으로 내린다. 두 파일은 08-B 승인 범위(엑셀/통계) 밖의 별도 로직 변경이다.
- 08-C(프론트) 진행 자체는 **가능**(08-B 계약이 확정·컴파일 성공). 단, 위 스코프 위반 2건의
  소속 Loop 확인/분리 처리는 08-C 착수와 병행해 정리 권고.

---

## 1. 항목별 검증 결과

### 1-1. 기존 로직 무변경 (최중요) — PASS
근거: `git diff` 상 대상 파일 모두 **추가만**.
- `DevicePointRepository.java`: diff 는 162행 이후 신규 메서드 4개(+doc) 삽입만. 기존
  `findForRefMapping / findForRefMappingPage / countMappedForRefMapping` 및 모든 bulk 쿼리
  본문 변경 없음.
- `DeviceController.java`: import 3줄 + 필드 1개 + 엔드포인트 3개(147행 이후) 추가만. 기존
  `/points/mapping`(GET) · `/points/ref-mapping`(PUT) 등 핸들러 로직 무변경.
- `DeviceService.getDevicePointsForMappingPage` / `updateDevicePointRefMappings` /
  `findForRefMappingPage` / `countMappedForRefMapping` **호출만**(재활용). 시그니처·본문 불변.
- 엑셀 반영은 신규 `DevicePointMappingExcelService.validateAndApply` 가
  `DeviceService.updateDevicePointRefMappings(List)` 를 그대로 호출
  (`DevicePointMappingExcelService.java:244`). 기존 매핑 조회/수정 회귀 없음.

### 1-2. template (다운로드) — PASS
근거: `DevicePointMappingExcelService.buildTemplate` (63-118), `DeviceController` diff.
- 전체 목록(페이징 없음)은 신규 `findAllForRefMapping` 사용. WHERE 절이 기존
  `findForRefMappingPage` 메인쿼리와 **동일**(deviceId / categoryIds / keyword 6컬럼 LIKE,
  ORDER BY deviceName, tagName). 차이는 의도된 미매핑 토글(`:unmapped`) 제거뿐 →
  "전체 범위" 요건과 일치.
- `resolveSmallCategoryIdsForFilter(categoryId)` + `categoryIds.isEmpty() → List.of()`
  가드가 기존 `getDevicePointsForMappingPage`(DeviceService:193-196)와 동일 패턴 → 필터 일관.
- 헤더/컬럼: `장비명|태그|포인트명|point_key|ref_device_code|ref_point_code` 정확
  (HEADERS 배열 49-52). ref 프리필: `nvl(p.getRefDeviceCode()) / nvl(p.getRefPointCode())`
  로 기존값 채움, 없으면 빈칸(101-106).
- Content-Type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`,
  Content-Disposition `attachment; filename=...; filename*=UTF-8''...`(RFC5987) 정상.
- try-with-resources: `XSSFWorkbook` + `ByteArrayOutputStream` (74-75) 모두 close. 워크북을
  byte[] 직렬화 후 반환하여 스트림 누수 없음.

### 1-3. import (업로드) — PASS (정책 일관성 확인)
근거: `importMappings`(125-174), `validateAndApply`(176-255).
- 헤더명 기반 컬럼 매칭(순서 비의존): `resolveColumnIndex`(274-283) 가 헤더명→인덱스 맵 구성,
  `requireColumn` 로 필수 3컬럼(point_key/ref_device_code/ref_point_code) 검증. 누락 시
  `IllegalArgumentException`.
- point_key 매칭: `findAllByPointKeyIn(keys)` 일괄 로딩 후 in-memory `Map` 조회(187-192,203).
  N+1 회피(1 쿼리).
- 행단위 검증 후 유효행만 반영: point_key 공백/미존재/both-or-neither(`hasDevice ^ hasPoint`)/
  파일내 ref 중복(`seenRefInFile`)/DB ref 중복(`findByRefDeviceCodeAndRefPointCodeAndPointIdNot`
  재활용) 각각 실패 사유 수집(200-231). 유효행만
  `DevicePointRefMappingRequest`(pointId, ref…) 로 만들어 `updateDevicePointRefMappings` 호출.
- 결과 DTO: `{total, applied, failed:[{row(1-based), pointKey, reason}]}` 계약 일치.
- 방어: 빈/누락 파일(`file.isEmpty()` 126), 시트 0개(136-138), 헤더행 없음(140-143),
  필수 헤더 누락(146-148), 완전 빈 행 skip(163-165), IO 오류→IllegalArgumentException(169-171).
- **트랜잭션 경계 / 부분반영 vs 롤백 정책 일관성 — 일관됨**:
  - 메서드 전체 `@Transactional`(124). 정책 = "사전 검증 통과분만 반영 + 실패목록 반환"(부분 반영).
  - 사전 검증(존재/both-or-neither/파일내·DB 중복)으로 기존 update 가 throw 하지 않도록 선제
    필터링. 검증 실패 행은 valid 에서 제외되어 반영 대상 아님 → "부분 반영"이 곧 정상 경로.
  - 검증을 통과한 valid 배치가 update 중 예외(경합 등)면 catch 후 `IllegalStateException`
    재던짐(246-251) → **트랜잭션 전체 롤백**. 즉 "일부만 커밋된 애매한 상태"를 남기지 않음.
  - 결론: 승인서 40행 권장 정책("유효행만 반영 + 실패목록, 중복검사는 트랜잭션 내")과
    **정확히 일치**. 부분반영(검증단)과 전건롤백(반영단 예외)의 경계가 명확·일관.
  - 참고(비결함): 검증 통과분 배치 반영 도중 특정 행 예외 시 통과분 전체가 롤백되므로,
    "일부 유효행은 반영·나머지는 실패"의 세밀도가 반영 단계 예외에는 적용되지 않음. 이는
    데이터 정합 우선(부분 커밋 금지) 선택으로 정책상 타당. 사용자 안내 문구만 후속 고려.
- MultipartFile `InputStream` + `XSSFWorkbook(in)` try-with-resources close(133-134).

### 1-4. stats (통계) — PASS
근거: `getStats`(262-272), `DeviceLocation`/`PointMappingStatsResponse`.
- 범위: category/keyword 기준. `total = countForRefMapping`(신규),
  `mapped = countMappedForRefMapping`(기존 재활용) 둘 다 동일 WHERE(deviceId/categoryIds/
  keyword). `total` 은 매핑 여부 조건만 제거 → 미매핑 토글과 무관하게 범위 전체.
- `mapped` 는 기존 페이지 응답의 `mappedCount`(DeviceService:201) 와 동일 산식 → 화면 카드와
  기존 요약치 정합.
- `categoryIds.isEmpty() → (0,0)` 가드(266-268) 일관.

### 1-5. 리포지토리 신규 4메서드 — PASS
- `countForRefMapping`(170-189): `countMappedForRefMapping` 에서 매핑 완료 AND 절만 제거한
  동일 WHERE. total 카운트로 정확.
- `findAllForRefMapping`(196-217): `findForRefMappingPage` 메인쿼리와 동일 WHERE + JOIN FETCH
  device / LEFT JOIN FETCH category + ORDER BY. 미매핑 토글만 제외. `JOIN FETCH` 로 template
  의 `device.getDeviceName()` N+1 회피.
- `findByPointKey` / `findAllByPointKeyIn`: 파생 쿼리. import 는 `findAllByPointKeyIn` 만
  사용(일괄) → N+1 없음. `findByPointKey` 는 계약상 존재하나 서비스에서 미사용(사용은 In 버전).
  잉여 메서드 1개는 무해(후속 정리 대상, WARNING 아님).
- 성능: template/stats/import 모두 IN 절 기반 벌크 조회. 대량 시 메모리는 후속 항목(아래).

### 1-6. 범위 준수 — WARNING
- PASS: `application.yml` 무변경, `build.gradle` 는 08-A POI 1줄만(neither 신규 의존성 추가).
- **위반 2건(device 하위이나 08-B 승인 범위 밖 로직 변경)**:
  1. `device/service/DeviceCategoryCascadeService.java` — 카테고리 삭제 로직을 orphanRemoval
     기반으로 교체(`parent.getChildren().remove(root)` … 기존 `deleteAll(toDelete)` 주석처리).
     엑셀/통계와 무관한 별개 기능 변경. 주석처리 코드 잔존(정리 필요).
  2. `device/dto/DeviceLocationResponse.java` — record 에 `meshType` 필드 추가. 08-B 무관.
  - 두 파일 모두 이번 QA 대상(엑셀 3종)과 무관하므로 08-B 산출물 품질에는 영향 없으나,
    "device 하위만, 지정 파일만" 스코프 관점에서 혼입 변경. 다른 Loop 산출물이면 해당
    Loop 로 귀속·별도 QA, 미승인 변경이면 롤백 필요.

### 1-7. 컴파일 — PASS
`./gradlew :hana-digitaltwin:compileJava -q` → EXIT 0(오류 없음).
DTO/서비스 시그니처(`DevicePointRefMappingRequest(pointId, refDeviceCode, refPointCode)`),
POI API 정상 해석.

---

## 2. 판정 요약표

| 항목 | 판정 |
|---|---|
| 1. 기존 로직 무변경(재활용) | PASS |
| 2. template | PASS |
| 3. import(부분반영/롤백 정책 일관) | PASS |
| 4. stats | PASS |
| 5. 리포지토리 4메서드 | PASS |
| 6. 범위 준수 | WARNING (스코프 위반 2건) |
| 7. 컴파일 | PASS |
| 전체 | WARNING |

## 3. 수정 요청 대상 AGENT
- 08-B 엑셀 산출물 자체: **없음**(수정 불요).
- 스코프 위반 2건 정리:
  - `DeviceCategoryCascadeService.java`(주석 코드 잔존 포함) → **spring-backend-agent**
    (소속 Loop 확인/귀속 또는 롤백. 미승인이면 롤백).
  - `DeviceLocationResponse.java` → **spring-backend-agent** (동일).
  - 처리 방식(귀속 vs 롤백)은 loop-orchestrator 가 소속 Loop 확인 후 결정.

## 4. 08-C 진행 가능 여부
- **가능**. 08-B 계약(template blob / import multipart 결과 / stats)이 확정·컴파일 성공.
  프론트는 이 계약대로 소비 가능.
- 조건: 스코프 위반 2건은 08-B 품질과 독립이므로 08-C 착수를 막지 않되, 병행 정리 권고.

## 5. 재검증 필요 여부
- 08-B 엑셀 산출물: **재검증 불요**.
- 스코프 위반 2건을 롤백/귀속 처리한 경우: 해당 파일 한정 재확인 필요.

## 6. 후속 항목(위험도)
1. **대용량 파일 스트리밍/메모리 (MEDIUM)**: template 는 `XSSFWorkbook`(DOM, 전건 메모리)
   + `autoSizeColumn`(폰트 메트릭 계산, 대량행 시 CPU/메모리 부담). import 는
   `XSSFWorkbook(InputStream)` 전체 로드 + `findAllForRefMapping`/`findAllByPointKeyIn` 전건
   메모리 적재. 수만 행 규모면 `SXSSFWorkbook`(쓰기)/streaming reader(읽기) + 업로드 크기 제한
   (multipart max-file-size) 검토.
2. **POI 보안 — XML entity/zip bomb (MEDIUM)**: 외부 업로드 xlsx 파싱. POI 5.2.5 는
   `ZipSecureFile` min inflate ratio/최대 엔트리 기본 방어가 있으나, 신뢰 불가 입력이므로
   업로드 크기·시트/행 상한, 필요 시 `IOUtils.setByteArrayMaxOverride` 등 방어 한도 명시 권고.
3. **import 트랜잭션 정책 (LOW, 정보성)**: 현재 "검증 통과분 부분 반영 + 반영단 예외 시 전건
   롤백"으로 일관. 반영 단계 부분 실패 시 전건 롤백되는 점을 사용자 결과 메시지/문서에 명확화.
4. **잉여/잔존 코드 (LOW)**: `findByPointKey` 미사용, DeviceCategoryCascadeService 주석처리
   블록 → 정리 권고(품질).
