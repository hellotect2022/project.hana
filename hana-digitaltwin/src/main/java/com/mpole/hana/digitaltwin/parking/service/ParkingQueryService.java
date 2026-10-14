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
 * <p>기간은 {@code date}(KST 하루)가 있으면 그날 00:00 ~ 다음날 00:00 이고, 없으면 {@code from}/{@code to} 를 그대로 쓴다.
 * 둘 다 없으면 기간 제한 없음.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParkingQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> STATUSES = Set.of("PARKED", "EXITED");

    private final ParkingSessionRepository repository;

    public Page<ParkingSessionResponse> sessions(LocalDate date, OffsetDateTime from, OffsetDateTime to,
                                                 String carNo, String status, Pageable pageable) {
        OffsetDateTime[] range = range(date, from, to);
        return repository.search(range[0], range[1], likeOf(carNo), normalizeStatus(status), pageable)
                .map(ParkingSessionResponse::from);
    }

    public ParkingSummary summary(LocalDate date, OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime[] range = range(date, from, to);
        return new ParkingSummary(range[0], range[1],
                repository.countIn(range[0], range[1]),
                repository.countOut(range[0], range[1]),
                repository.countParked());
    }

    private static OffsetDateTime[] range(LocalDate date, OffsetDateTime from, OffsetDateTime to) {
        if (date != null) {
            return new OffsetDateTime[]{
                    date.atStartOfDay(KST).toOffsetDateTime(),
                    date.plusDays(1).atStartOfDay(KST).toOffsetDateTime()};
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from 은 to 보다 앞이어야 합니다.");
        }
        return new OffsetDateTime[]{from, to};
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
