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
 * 주차 입차 기록 (2026-10-13) — {@code tbl_parking_in}.
 *
 * <p><b>쓰기는 gateway</b>(ParkingSyncService)가 5분마다 주차 MSSQL 프로시저 {@code UP_SearchInCarinfo} 결과를
 * 주차일련번호 기준으로 UPSERT 한다. digitaltwin 은 테이블 정의(ddl-auto)와 조회만 한다.</p>
 *
 * <p>{@code collected_at}/{@code updated_at} 은 gateway 가 값을 넣지 않고 DB 기본값 {@code now()} 에 맡기므로
 * columnDefinition 으로 DEFAULT 를 명시한다(없으면 gateway INSERT 가 NOT NULL 위반으로 실패).</p>
 */
@Entity
@Table(name = "tbl_parking_in", indexes = {
        @Index(name = "idx_parking_in_at", columnList = "in_at"),
        @Index(name = "idx_parking_in_car", columnList = "car_no")
})
@Comment("주차 입차 기록(주차 MSSQL UP_SearchInCarinfo 를 gateway 가 5분마다 적재). 2026-10-13")
@Getter
@NoArgsConstructor
public class ParkingIn {

    @Id
    @Column(name = "parking_seq", length = 30)
    @Comment("주차일련번호. 입차 때 생기고 출차 기록에도 같은 번호로 온다 — 입차·출차를 묶는 키")
    private String parkingSeq;

    @Column(name = "in_date", nullable = false)
    @Comment("입차일자")
    private LocalDate inDate;

    @Column(name = "in_time", nullable = false)
    @Comment("입차시각")
    private LocalTime inTime;

    @Column(name = "in_at", nullable = false)
    @Comment("입차일자+시각(KST). 조회·정렬용")
    private OffsetDateTime inAt;

    @Column(name = "car_no", length = 20)
    @Comment("차량번호")
    private String carNo;

    @Column(name = "card_no", length = 30)
    @Comment("입차카드번호. 0 = 카드 없음")
    private String cardNo;

    @Column(name = "device_name", length = 50)
    @Comment("장비명(B2입구 등)")
    private String deviceName;

    @Column(name = "in_type", length = 20)
    @Comment("입차구분")
    private String inType;

    @Column(name = "customer_type", length = 20)
    @Comment("입차고객구분")
    private String customerType;

    @Column(name = "exit_status", length = 10)
    @Comment("출차여부. 차가 나가면 출차 로 바뀐다(gateway 가 변경분만 UPDATE)")
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
