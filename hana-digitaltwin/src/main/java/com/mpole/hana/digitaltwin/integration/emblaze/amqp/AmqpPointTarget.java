package com.mpole.hana.digitaltwin.integration.emblaze.amqp;

/**
 * RabbitMQ 메시지 필드 1개가 기록될 대상 포인트.
 *
 * <p>emblaze 는 <b>모든 데이터를 조명제어기 UUID 하나로</b> 보낸다 — 센서·스위치의 구별용 ID 를
 * 따로 주지 않는다. 대신 큐(light/space/enocean)마다 실려오는 <b>필드명이 다르므로</b>,
 * {@code (device_uuid, 필드명)} 을 키로 쓰면 장비가 유일하게 결정된다.</p>
 *
 * <pre>
 *   같은 uuid 776EF4D2-… 에 장비 3대가 걸려 있다
 *     light   present_level    → ELEC_LE_RL_WC-4F-M01-B01   (조명)
 *     space   pir_accumulation → ELEC_SR_OCC_WC-4F-M01-B01  (재실센서)
 *     enocean switch_number    → ELEC_BTN_TB_WC-4F-M01-B01  (버튼)
 * </pre>
 *
 * <p>매핑 원본은 {@code tbl_device_point} 의 {@code ref_device_code}(uuid) ·
 * {@code ref_point_code}(벤더 필드명) 다. 예전처럼 {@code tbl_device.property_info} 에
 * uuid 를 넣지 않는다 — 포인트마다 소스가 다를 수 있고, 그 정보는 포인트가 들고 있어야 한다.</p>
 *
 * @param deviceId        기록 대상 장비
 * @param deviceKey       current_value 신규 행 생성 시 필요
 * @param tagName         current_value 의 {@code points[].tagName} — 보통 필드명과 같다
 * @param categoryCode    RL · OCC · TB
 * @param zoneId          배치된 부스 구역
 * @param zoneCode        부스 연동코드 (WC_4F_M01_B01) — HQS {@code wcBoothId}
 * @param zoneName        부스 이름
 * @param parentZoneCode  화장실 연동코드 (WC_4F_M01) — HQS {@code wcId}
 * @param parentZoneName  화장실 이름
 * @param floorName       층 이름 — HQS {@code florId}
 */
public record AmqpPointTarget(
        Long deviceId,
        String deviceKey,
        String tagName,
        String categoryCode,
        Long zoneId,
        String zoneCode,
        String zoneName,
        String parentZoneCode,
        String parentZoneName,
        String floorName
) {
}
