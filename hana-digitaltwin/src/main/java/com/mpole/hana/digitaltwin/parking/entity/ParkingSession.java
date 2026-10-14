package com.mpole.hana.digitaltwin.parking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.time.OffsetDateTime;

/**
 * 주차 건 1개 = 입차 + 출차를 한 줄로 (2026-10-13). <b>읽기 전용, 테이블 아님</b>.
 *
 * <p>DB 뷰 대신 {@code @Subselect} 로 쿼리를 엔티티에 붙였다 — 뷰를 손으로 DROP/CREATE 할 일이 없고 배포만으로 바뀐다.
 * ddl-auto 는 이 엔티티의 테이블을 만들지 않는다.</p>
 *
 * <ul>
 *   <li>FULL OUTER JOIN — 적재를 시작하기 전에 들어와 그 뒤에 나간 차처럼 <b>출차만 있는 건</b>도 나온다.</li>
 *   <li>status — 출차 기록이 있거나 입차 기록의 출차여부가 '출차' 면 EXITED, 아니면 PARKED(주차중).
 *       입차 결과가 먼저 '출차' 로 바뀌고 출차 결과가 아직 안 들어온 5분 틈도 EXITED 로 본다.</li>
 * </ul>
 */
@Entity
@Immutable
@Subselect("""
        SELECT COALESCE(i.parking_seq, o.parking_seq)   AS parking_seq,
               COALESCE(i.car_no, o.car_no)             AS car_no,
               i.in_at, i.device_name AS in_device,  i.card_no AS in_card_no,
               o.out_at, o.device_name AS out_device, o.card_no AS out_card_no,
               CASE WHEN o.parking_seq IS NOT NULL OR i.exit_status = '출차' THEN 'EXITED'
                    ELSE 'PARKED' END                   AS status,
               (i.parking_seq IS NOT NULL)              AS has_in,
               (o.parking_seq IS NOT NULL)              AS has_out
          FROM tbl_parking_in i
          FULL OUTER JOIN tbl_parking_out o ON o.parking_seq = i.parking_seq
        """)
@Synchronize({"tbl_parking_in", "tbl_parking_out"})
@Getter
@NoArgsConstructor
public class ParkingSession {

    @Id
    @Column(name = "parking_seq")
    private String parkingSeq;

    @Column(name = "car_no")
    private String carNo;

    @Column(name = "in_at")
    private OffsetDateTime inAt;

    @Column(name = "in_device")
    private String inDevice;

    @Column(name = "in_card_no")
    private String inCardNo;

    @Column(name = "out_at")
    private OffsetDateTime outAt;

    @Column(name = "out_device")
    private String outDevice;

    @Column(name = "out_card_no")
    private String outCardNo;

    /** PARKED(주차중) / EXITED(출차) */
    @Column(name = "status")
    private String status;

    /** 입차 기록이 있는가 */
    @Column(name = "has_in")
    private boolean hasIn;

    /** 출차 기록이 있는가 */
    @Column(name = "has_out")
    private boolean hasOut;
}
