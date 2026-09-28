package com.mpole.hana.digitaltwin.external.emblaze.mapping;

/**
 * RabbitMQ 메시지 필드 1개가 기록될 대상 포인트 + <b>그 장비가 속한 부스의 신원</b>.
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
 * <h3>부스 신원은 zone 이 아니라 그룹에서 온다</h3>
 * <p>예전에는 {@code zone_code}(부스) · 부모 {@code zone_code}(화장실)를 실어 왔다. 그 방식은
 * <b>부스 zone 이 있는 층에서만 동작</b>했다 — 부스 zone 은 4F·10F·14F 의 73개뿐이고,
 * 나머지 13개 층은 화장실 zone 에 장비를 붙였기 때문에 부모가 없어
 * {@code wcId} 가 null, {@code wcBoothId} 가 화장실 코드로 나갔다(376개 중 304개).</p>
 *
 * <p>지금은 {@code tbl_device_group}(부스 그룹)에서 가져온다. <b>조회 API(boothStatus)가 이미
 * 같은 출처를 쓴다</b> — 푸시와 조회가 같은 부스를 다른 이름으로 부르면 HQS 가 둘을 짝지을 수
 * 없다. 정본이 하나여야 한다.</p>
 *
 * <pre>
 *   groupCode  = tbl_device_group.group_code             → HQS wcBoothId
 *   wcCode     = attributes-&gt;&gt;'wcCode'                    → HQS wcId
 *   gender     = attributes-&gt;&gt;'gender' (없으면 코드에서 유추) → HQS gender
 *   floorName  = placement → zone → floor                → HQS florId
 * </pre>
 *
 * <p>{@code floorName} 만 여전히 배치에서 온다 — 층은 "장비가 어디 있나"라서 배치가 정본이고,
 * 조회 API 도 같은 경로로 구한다.</p>
 *
 * <p><b>매번 조회하지 않는다.</b> 이 값들은 {@link AmqpPointMappingCache} 가 기동 시 한 번 읽어
 * 메모리에 들고 있다. 버튼이 눌릴 때마다 DB 를 치면 누름 빈도만큼 부하가 생긴다.</p>
 *
 * @param deviceId   기록 대상 장비
 * @param deviceKey  current_value 신규 행 생성 시 필요
 * @param tagName    current_value 의 {@code points[].tagName} — 보통 필드명과 같다
 * @param groupCode  부스 그룹 코드 ({@code WC_4F_M01_B01}) — HQS {@code wcBoothId}
 * @param wcCode     화장실 코드 ({@code WC_4F_M01}) — HQS {@code wcId}
 * @param gender     {@code M} 또는 {@code F} — HQS {@code gender}
 * @param floorName  층 이름 ({@code 4F}) — HQS {@code florId}
 */
public record AmqpPointTarget(
        Long deviceId,
        String deviceKey,
        String tagName,
        String groupCode,
        String wcCode,
        String gender,
        String floorName
) {
}
