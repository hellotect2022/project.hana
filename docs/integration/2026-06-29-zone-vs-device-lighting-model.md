# 조명/화재 구역 모델링 분석 — "구역 vs device"

## 질문
조명 구역(3D 박스로 보이는 영역)을 **구역(Zone)으로 관리**해야 하나, **device로 관리**해야 하나?
2D 평면 구역 정보를 3D로 어떻게 저장하나?

## 결론 (요약)
3D 박스는 **device가 아니라 Zone(위치)** 다. 단, 그 박스의 **상태(ON/OFF/Fault)는 그 안의 실제
조명 device들에서 집계**된다. 즉 "구역 vs device"가 아니라 **둘 다 + 표현정책** 3계층이며,
기존 코드가 이미 그렇게 설계되어 있다.

## 기존 코드 근거 (이미 구현된 구조)
- `UnityLocZone`(tbl_unity_loc_zone): zoneId, floor, zoneName, **zoneMeshName(=mesh_collider)**.
  → 3D 박스(영역)의 **기하/위치**. 자기 텔레메트리 없음. (WA-ZONE에서 등록)
- `Device` + `DevicePlacement.unityZone`: 실제 조명 장비가 **Zone에 배치**됨.
  (`LightingDeviceRepository.fetchForLightingByZone` = 카테고리(조명) + zoneId 로 device 조회)
- `DeviceCurrentValue`(realtime): device별 실시간 ON/OFF/dimming.
- `buildingLighting.BuildingLightingStatusService` → `LightingZoneStatusResponse`:
  **zone별로 device 상태를 집계**해 deviceCount/onCount/offCount/state(ON/OFF/UNKNOWN)/dimming/
  commStatus(Fault)를 **런타임 계산**. (저장하지 않음)

## 3계층 모델
| 개념 | 무엇 | 저장 위치 | 상태 |
|---|---|---|---|
| 3D 박스(영역·위치) | `UnityLocZone` (mesh_collider) | tbl_unity_loc_zone (있음) | — (기하) |
| 실제 조명 | `Device`(조명 카테고리) + placement.unityZone | tbl_device/placement (있음) | DeviceCurrentValue 실시간 |
| 구역 상태(ON/OFF/Fault) | 집계값 | 저장 안 함 | buildingLighting 런타임 파생 |
| **조명/화재 표현정책** | 색상코드·투명도·활성(구역명) | **신규 tbl_zone_display_policy** | — |

## "device로 만들면 안 되는 이유"
- 박스는 **영역(기하)** 이고 자기 포인트/텔레메트리가 없다. 상태는 그 안 실제 조명 device들에서
  집계된다. 박스를 device로 만들면 기하·하드웨어가 뒤섞이고, 같은 박스를 조명/화재 양쪽에서
  재사용하지 못한다.

## "Zone에 색상칼럼만 추가하면 안 되는 이유"
- WA-SPACE-LIGHT는 **조명 구역 / 화재감지 구역** 두 탭이 "동일 레이아웃, 데이터만 구분".
  같은 물리 Zone이 조명 표시영역이자 화재 표시영역이 될 수 있고 색/정책이 다르다.
  → 표현정책은 **(zoneId × systemType) 별 별도 레코드**여야 한다(=Zone 속성 아님).

## 권장 (WA-SPACE-LIGHT 신규 엔티티)
`tbl_zone_display_policy`
- policyId
- zoneId → UnityLocZone (위치, WA-ZONE에서 등록한 Zone과 연결)
- systemType: LIGHTING | FIRE_DETECTION (탭 구분)
- displayName (구역명, 예 "조명구역 B1F-01")
- colorCode (#00CC88), opacity (0~100), active (toggle)
- (화재 전용) evacuationAutoShow (피난대피로 자동 표시 연동) — nullable
- 상태(ON/OFF/Fault)는 저장하지 않고 buildingLighting 집계로 표시

## 2D → 3D 저장 방법
- 3D 박스 자체는 Unity에서 미리 만든 mesh를 `zoneMeshName(mesh_collider)`로 식별 → Zone에 1회 등록.
- 관리자는 zoneId ↔ mesh 매핑(이미 WA-ZONE) + 표현정책(색/투명도/활성)만 저장.
- 기존 2D 평면 구역 정보는 UnityLocZone(위치)로 이전/연결하고, 색/표시는 표현정책으로 분리.

## 결정 필요
1. 위 3계층(Zone + Device + 신규 표현정책) 채택?
2. 채택 시 백엔드(tbl_zone_display_policy 엔티티/CRUD API) + 프론트(WA-SPACE-LIGHT 화면) 구현 범위.
