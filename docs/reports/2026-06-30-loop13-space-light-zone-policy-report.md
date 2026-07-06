# AGENT 작업 보고서

## 1. 작업 AGENT
integration-architect(설계) → spring-backend-agent + react-frontend-agent (구현)

## 2. 작업 일시
2026-06-30 (Loop 13)

## 3. 작업 목표
조명/화재 "구역" 표현 화면(WA-SPACE-LIGHT) 구현. 사전 결정: 3계층 분리
(Zone 위치 + Device 조명 + 신규 표현정책), 전체 구현 승인.

## 4. 설계 결론 (구역 vs device)
3D 박스는 device가 아니라 **Zone(UnityLocZone, mesh_collider)**. 상태(ON/OFF/Fault)는
그 안의 조명 **Device** 집계(buildingLighting)에서 파생. 표시 방법(색/투명도/활성)만 신규
**표현정책**으로 저장. (분석: docs/integration/2026-06-29-zone-vs-device-lighting-model.md)

## 5. 백엔드 (신규 space 도메인)
- `space/entity/ZoneSystemType.java` — enum LIGHTING/FIRE_DETECTION(영문 저장/한글 label)
- `space/entity/ZoneDisplayPolicy.java` — tbl_zone_display_policy
  (zone→UnityLocZone, systemType, displayName, colorCode, opacity, active, evacuationAutoShow;
   unique(zone_id, system_type)). DateEntity/columnDefinition 미사용 → ddl-auto=update 신규
   테이블 생성 안전.
- `space/repository/ZoneDisplayPolicyRepository.java` — systemType별 zone fetch-join 목록 + 중복검사
- `space/dto/*` — Request/Response(zone·building·floor 포함)/UpdateItem/SummaryResponse
- `space/service/ZoneDisplayPolicyService.java` — 목록/요약/등록/일괄저장/삭제.
  요약(전체/ON/OFF/Fault): 조명은 buildingLighting 집계(zoneId 매핑, commStatus≠NORMAL→Fault),
  화재는 상태 소스 없어 전체수만.
- `space/api/ZoneDisplayPolicyController.java` — `/api/space/zone-policies`
  (GET 목록 ?systemType, GET /summary, POST 등록, PUT 일괄저장, DELETE)

## 6. 프론트 (05-06)
- `services/spaceZoneService.ts` — 목록/요약/등록/일괄저장/삭제
- `pages/zone/SpaceLightManagePage.tsx` — 탭(조명/화재), 현황 카드(전체/ON/OFF/Fault),
  구역 표 인라인 편집(구역명/색상/투명도/활성, 화재 탭은 "피난대피로 자동표시" 토글 추가),
  정책 저장(일괄 PUT), 구역 추가, 삭제. Tab/Toggle/Input 등 디자인시스템 사용.
- `components/modal/zone/ZonePolicyCreateModal.tsx` — Zone(트리에서 선택)+표시속성 등록
- `constants/menuData.tsx` — "공간·조명/화재감지" 메뉴(구역 설정 그룹, /zone/space-light)

## 7. 검증
- 백엔드 compileJava 성공(exit 0). 프론트 typecheck 0, build 성공(exit 0).

## 8. 남은 이슈 / 다음 Loop 제안
- 화재감지 구역 상태(ON/OFF/Fault) 백엔드 집계 소스 없음 → 전체수만 표시. 필요 시 화재 도메인 추가.
- 표현정책 색/투명도/활성을 3D(Unity)·뷰어에서 실제로 소비하는 연동은 별도.
- 구역명 자동 생성 규칙(예: 조명구역 {층}-{seq}) 필요 시 추가.
- 실 DB 런타임 확인(등록→저장→집계 카드) 권장.

## 9. QA 요청 사항
- 3계층 분리 정합성, 표현정책 CRUD/일괄저장, 조명 요약 집계, 컴파일/빌드 — 확인 완료.
