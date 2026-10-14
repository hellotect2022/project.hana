package com.mpole.hana.digitaltwin.parking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * 주차 출차 기록 (2026-10-13) — {@code tbl_parking_out}.
 *
 * <p><b>쓰기는 gateway</b>(ParkingSyncService)가 5분마다 주차 MSSQL 프로시저 {@code UP_SearchoutCarinfo}(출차일 기준) 결과를
 * 주차일련번호 기준으로 UPSERT 한다. 주차일련번호는 입차 때 받은 번호 그대로라 {@link ParkingIn} 과 같은 키로 묶인다.</p>
 */
@Entity
@Table(name = "tbl_parking_out", indexes = {
        @Index(name = "idx_parking_out_at", columnList = "out_at"),
        @Index(name = "idx_parking_out_car", columnList = "car_no")
})
@Comment("주차 출차 기록(주차 MSSQL UP_SearchoutCarinfo 를 gateway 가 5분마다 적재). 2026-10-13")
@Getter
@NoArgsConstructor
public class ParkingOut {

    @Id
    @Column(name = "parking_seq", length = 30)
    @Comment("주차일련번호(입차와 같은 번호)")
    private String parkingSeq;

    @Column(name = "out_date", nullable = false)
    @Comment("출차일자")
    private LocalDate outDate;

    @Column(name = "out_time", nullable = false)
    @Comment("출차시각")
    private LocalTime outTime;

    @Column(name = "out_at", nullable = false)
    @Comment("출차일자+시각(KST). 조회·정렬용")
    private OffsetDateTime outAt;

    @Column(name = "car_no", length = 20)
    @Comment("차량번호")
    private String carNo;

    @Column(name = "card_no", length = 30)
    @Comment("출차카드번호. 0 = 카드 없음")
    private String cardNo;

    @Column(name = "device_name", length = 50)
    @Comment("장비명(B2출구#1 등)")
    private String deviceName;

    @Column(name = "out_type", length = 20)
    @Comment("출차구분")
    private String outType;

    @Column(name = "customer_type", length = 20)
    @Comment("출차고객구분")
    private String customerType;

    @Column(name = "exit_status", length = 10)
    @Comment("출차여부")
    private String exitStatus;

    @Column(name = "car_kind", length = 20)
    @Comment("차량종류")
    private String carKind;

    @Column(name = "collected_at", insertable = false, updatable = false,
            columnDefinition = "timestamptz NOT NULL DEFAULT now()")
    @Comment("처음 적재된 시각(DB 기본값)")
    private OffsetDateTime collectedAt;

    @Column(name = "updated_at", insertable = false, updatable = false,
            columnDefinition = "timestamptz NOT NULL DEFAULT now()")
    @Comment("마지막으로 값이 바뀐 시각(gateway UPSERT 가 갱신)")
    private OffsetDateTime updatedAt;
}
