package com.mpole.hana.digitaltwin.parking.service;

import com.mpole.hana.digitaltwin.parking.dto.ParkingSessionResponse;
import com.mpole.hana.digitaltwin.parking.dto.ParkingSummary;
import com.mpole.hana.digitaltwin.parking.repository.ParkingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * 주차 입차·출차 조회 (2026-10-13). 데이터는 gateway 가 5분마다 적재한 tbl_parking_in/out.
 *
 * <p>기간은 날짜(KST)로 받는다. {@code date} 가 있으면 그날 하루, 없으면 {@code from} 00:00 ~ {@code to} 다음날 00:00(to 포함).
 * 둘 다 없으면 기간 제한 없음.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParkingQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> STATUSES = Set.of("PARKED", "EXITED");

    private final ParkingSessionRepository repository;

    public Page<ParkingSessionResponse> sessions(LocalDate date, LocalDate from, LocalDate to,
                                                 String carNo, String status, Pageable pageable) {
        OffsetDateTime[] range = range(date, from, to);
        return repository.search(range[0], range[1], likeOf(carNo), normalizeStatus(status), pageable)
                .map(ParkingSessionResponse::from);
    }

    public ParkingSummary summary(LocalDate date, LocalDate from, LocalDate to) {
        OffsetDateTime[] range = range(date, from, to);
        return new ParkingSummary(range[0], range[1],
                repository.countIn(range[0], range[1]),
                repository.countOut(range[0], range[1]),
                repository.countParked());
    }

    /**
     * 날짜(KST) → 조회 구간 [시작, 끝). 2026-10-16: from/to 를 일시가 아니라 날짜로 받는다.
     * <pre>
     *   date            → date 00:00 ~ date+1 00:00
     *   from ~ to       → from 00:00 ~ to+1 00:00  (to 날짜 포함)
     *   from 만 / to 만  → 반대쪽은 제한 없음
     * </pre>
     */
    private static OffsetDateTime[] range(LocalDate date, LocalDate from, LocalDate to) {
        if (date != null) {
            return new OffsetDateTime[]{startOf(date), startOf(date.plusDays(1))};
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from 은 to 와 같거나 앞이어야 합니다.");
        }
        return new OffsetDateTime[]{
                from == null ? null : startOf(from),
                to == null ? null : startOf(to.plusDays(1))};
    }

    private static OffsetDateTime startOf(LocalDate d) {
        return d.atStartOfDay(KST).toOffsetDateTime();
    }

    /** 차량번호 부분 일치. 공백은 무시 */
    private static String likeOf(String carNo) {
        if (carNo == null || carNo.isBlank()) return null;
        return "%" + carNo.replace(" ", "") + "%";
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.trim().toUpperCase();
        if (!STATUSES.contains(s)) {
            throw new IllegalArgumentException("status 는 PARKED(주차중) 또는 EXITED(출차) 입니다. 받은 값=" + status);
        }
        return s;
    }
}
