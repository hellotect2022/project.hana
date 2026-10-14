package com.mpole.hana.digitaltwin.parking.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.parking.dto.ParkingOccupancy;
import com.mpole.hana.digitaltwin.parking.dto.ParkingSessionResponse;
import com.mpole.hana.digitaltwin.parking.dto.ParkingSummary;
import com.mpole.hana.digitaltwin.parking.service.ParkingOccupancyService;
import com.mpole.hana.digitaltwin.parking.service.ParkingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 주차 입차·출차 조회 API (2026-10-13).
 *
 * <p>데이터는 gateway 가 주차 MSSQL(DRPKDBV5) 프로시저를 5분마다 불러 적재한 것이다 — 최대 5분 늦을 수 있다.</p>
 */
@RestController
@RequestMapping("/api/parking")
@RequiredArgsConstructor
@Tag(name = "Parking", description = "주차 입차·출차 기록 조회")
public class ParkingController {

    private final ParkingQueryService parkingQueryService;
    private final ParkingOccupancyService parkingOccupancyService;

    @Operation(
            summary = "주차 건 목록",
            description = """
                    입차·출차를 주차일련번호로 묶은 한 줄씩. 최근 움직임(출차 있으면 출차, 없으면 입차) 순.
                    · status = PARKED(주차중) / EXITED(출차). 출차 기록이 있거나 입차 기록의 출차여부가 '출차' 면 EXITED
                    · 기간 필터는 "그 기간에 입차 또는 출차가 있었던 건". date 가 있으면 그날(KST) 하루, 없으면 from~to
                    · hasIn=false 는 적재 시작 전에 들어온 차(출차만 있음), stayMinutes 는 입차·출차가 둘 다 있을 때만
                    """,
            parameters = {
                    @Parameter(name = "date", description = "하루(KST). 예 2026-10-02. 있으면 from/to 무시", example = "2026-10-02"),
                    @Parameter(name = "from", description = "기간 시작(포함, ISO-8601)"),
                    @Parameter(name = "to", description = "기간 끝(미포함, ISO-8601)"),
                    @Parameter(name = "carNo", description = "차량번호 부분 일치"),
                    @Parameter(name = "status", description = "PARKED(주차중) / EXITED(출차). 생략하면 전체")
            }
    )
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<ApiResponse.PageResponse<ParkingSessionResponse>>> sessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String carNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("주차 건 목록",
                parkingQueryService.sessions(date, from, to, carNo, status, PageRequest.of(page, size))));
    }

    @Operation(
            summary = "주차 집계",
            description = "기간 안 입차 수·출차 수, 그리고 지금 주차중인 차량 수(기간과 무관). 기간 규칙은 목록과 같다.",
            parameters = {
                    @Parameter(name = "date", description = "하루(KST). 있으면 from/to 무시", example = "2026-10-02"),
                    @Parameter(name = "from", description = "기간 시작(포함, ISO-8601)"),
                    @Parameter(name = "to", description = "기간 끝(미포함, ISO-8601)")
            }
    )
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ParkingSummary>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return ResponseEntity.ok(ApiResponse.success("주차 집계", parkingQueryService.summary(date, from, to)));
    }

    @Operation(
            summary = "주차면 점유 현황 (층별)",
            description = """
                    주차면 센서 기준 전체·현재 주차·잔여 대수와 층별 내역. 화면의 Total / 현재 주차 대수 / 잔여 주차 대수에 그대로 쓴다.
                    · 대상: 장비 분류 코드 PA-C, PA-D, PA-E, PA-R. 장비 1대 = 주차면 1칸
                    · 상태: 현재값의 st — Y = 주차(parked), N = 빈자리(available), 값 없음·그 밖 = unknown
                    · total = parked + available + unknown. 잔여는 N 인 칸만 센다(값 없는 센서는 빈자리로 치지 않음)
                    · floors: 위층부터. 층 배치가 없는 센서는 floorId·floorName 이 null 인 한 줄로 맨 뒤
                    · 입출차 기록(/sessions)과 무관하게 센서 현재값으로 계산한다
                    """
    )
    @GetMapping("/occupancy")
    public ResponseEntity<ApiResponse<ParkingOccupancy>> occupancy() {
        return ResponseEntity.ok(ApiResponse.success("주차면 점유 현황", parkingOccupancyService.occupancy()));
    }
}
