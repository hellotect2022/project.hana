package com.mpole.hana.digitaltwin.parking.dto;

import com.mpole.hana.digitaltwin.parking.entity.ParkingSession;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 주차 건 1행 (2026-10-13) — 입차·출차를 한 줄로.
 *
 * @param status      PARKED(주차중) / EXITED(출차)
 * @param hasIn       입차 기록이 있는가(적재 시작 전에 들어온 차는 false 일 수 있다)
 * @param hasOut      출차 기록이 있는가(EXITED 인데 false 면 입차 기록의 출차여부만 먼저 바뀐 5분 틈)
 * @param stayMinutes 주차 시간(분). 입차·출차가 둘 다 있을 때만
 */
public record ParkingSessionResponse(
        String parkingSeq,
        String carNo,
        String status,
        OffsetDateTime inAt,
        String inDevice,
        String inCardNo,
        OffsetDateTime outAt,
        String outDevice,
        String outCardNo,
        boolean hasIn,
        boolean hasOut,
        Long stayMinutes
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static ParkingSessionResponse from(ParkingSession s) {
        OffsetDateTime in = kst(s.getInAt());
        OffsetDateTime out = kst(s.getOutAt());
        Long stay = (in != null && out != null) ? Duration.between(in, out).toMinutes() : null;
        return new ParkingSessionResponse(
                s.getParkingSeq(), s.getCarNo(), s.getStatus(),
                in, s.getInDevice(), s.getInCardNo(),
                out, s.getOutDevice(), s.getOutCardNo(),
                s.isHasIn(), s.isHasOut(), stay);
    }

    /** 응답 시각은 KST(+09:00) 로 */
    private static OffsetDateTime kst(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(KST).toOffsetDateTime();
    }
}
