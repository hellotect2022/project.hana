package com.mpole.hana.digitaltwin.event.api;

import com.mpole.hana.digitaltwin.event.dto.AlarmHistoryRow;
import com.mpole.hana.digitaltwin.event.dto.AlarmHistoryStats;
import com.mpole.hana.digitaltwin.event.dto.AlarmRuleOption;
import com.mpole.hana.digitaltwin.event.dto.EventResponse;
import com.mpole.hana.digitaltwin.event.service.EventService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 이벤트 조회 API.
 *
 * <p>발생·해소·재활성·확인(ack) 쓰기는 전부 event-server(R2DBC, 웹소켓 ACK)가 담당한다.
 * digitaltwin 은 조회만 한다. 2026-10-06 삭제: 원시 로그 {@code GET /}, 화면 조립 {@code GET /{id}/display},
 * REST 확인 {@code POST /{id}/ack}(웹소켓 ACK 로 단일화), 그 전에 {@code /{id}/actions*}(tbl_event_action 폐기).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/event")
@RequiredArgsConstructor
@Tag(name = "Event", description = "이벤트 조회 API")
public class EventController {

    private final EventService eventService;

    @Operation(
            summary = "알람 이력 조회 (공통 화면용)",
            description = """
                    알람 이력 화면(명칭·위치·알람명·완료여부·발생일시) 전용 조회입니다.
                    전력감시·엘리베이터·소방방재·태양광·출입통제·화장실 물리버튼 화면이 모두 이 API 를 씁니다.

                    · 명칭 = 장비명, 위치 = 장비 배치 층(조회 시 조인)
                    · 알람명 = 규칙 이름(ruleName)
                    · **완료 여부 = ackedAt 존재 여부** (관제자 확인. 2026-10-02 기획 확정 — 알람을 닫는 유일한 행위)
                    · 클라이언트 기동 시 `completed=false` 로 미확인 알람을 복구한다. clearedAt(해소)·occurrenceCount(재발 횟수)는 참고값
                    · 각 행의 `simulated` 가 true 면 모의 이벤트(SOP 훈련, /api/event/simulate 로 발생·확인). `simulated` 필터로 모의만/실제만 (2026-10-07)
                    """,
            parameters = {
                    @Parameter(name = "categoryId", description = "장비 카테고리. 대·중분류를 넘기면 하위 소분류 전체로 확장", example = "12"),
                    @Parameter(name = "floorId", description = "층 ID(위치 필터)", example = "4"),
                    @Parameter(name = "ruleId", description = "알람명(규칙) ID", example = "7"),
                    @Parameter(name = "completed", description = "true=완료(확인됨), false=미완료(미확인), 생략=전체", example = "false"),
                    @Parameter(name = "simulated", description = "true=모의(SOP 훈련)만, false=실제만, 생략=전체", example = "false"),
                    @Parameter(name = "q", description = "검색어 — 알람명·장비명·장비키 부분일치(대소문자 무시)", example = "청소"),
                    @Parameter(name = "level", description = "심각도(CAUTION/WARNING/CRITICAL)"),
                    @Parameter(name = "deviceId", description = "특정 장비만"),
                    @Parameter(name = "from", description = "발생시각 시작(ISO-8601). 화면 기본은 최근 7일"),
                    @Parameter(name = "to", description = "발생시각 종료(ISO-8601)")
            }
    )
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<ApiResponse.PageResponse<AlarmHistoryRow>>> history(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) Boolean simulated,
            @RequestParam(required = false) String q,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<AlarmHistoryRow> result = eventService.getAlarmHistory(
                level, ruleId, deviceId, floorId, categoryId, completed, simulated, q, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success("알람 이력 조회", result));
    }

    @Operation(
            summary = "알람 이력 집계 (화면 상단 카드)",
            description = "/history 와 같은 필터로 발행 이벤트 수·완료(확인)·미완료(미확인) 건수를 반환합니다. "
                    + "표와 숫자가 같은 조건이어야 하므로 파라미터를 /history 와 똑같이 넘기세요(page/size/completed 제외).",
            parameters = {
                    @Parameter(name = "categoryId", description = "장비 카테고리(대·중분류는 하위 소분류로 확장)"),
                    @Parameter(name = "floorId", description = "층 ID"),
                    @Parameter(name = "ruleId", description = "알람명(규칙) ID"),
                    @Parameter(name = "simulated", description = "true=모의(SOP 훈련)만, false=실제만, 생략=전체"),
                    @Parameter(name = "q", description = "검색어 — 알람명·장비명·장비키 부분일치"),
                    @Parameter(name = "level", description = "심각도"),
                    @Parameter(name = "deviceId", description = "특정 장비만"),
                    @Parameter(name = "from", description = "발생시각 시작(ISO-8601)"),
                    @Parameter(name = "to", description = "발생시각 종료(ISO-8601)")
            }
    )
    @GetMapping("/history/stats")
    public ResponseEntity<ApiResponse<AlarmHistoryStats>> historyStats(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean simulated,
            @RequestParam(required = false) String q,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        AlarmHistoryStats stats = eventService.getAlarmHistoryStats(
                level, ruleId, deviceId, floorId, categoryId, simulated, q, from, to);
        return ResponseEntity.ok(ApiResponse.success("알람 이력 집계", stats));
    }

    @Operation(
            summary = "알람명 목록 (드롭다운)",
            description = "실제로 발생한 적 있는 알람명만 반환합니다. 한 번도 뜨지 않은 규칙은 제외됩니다.",
            parameters = {
                    @Parameter(name = "from", description = "이 시각 이후 발생분만. 생략 시 전체 기간")
            }
    )
    @GetMapping("/history/rules")
    public ResponseEntity<ApiResponse<List<AlarmRuleOption>>> historyRules(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from
    ) {
        return ResponseEntity.ok(ApiResponse.success("알람명 목록", eventService.getAlarmRuleOptions(from)));
    }

    @Operation(
            summary = "활성 이벤트 목록 조회",
            description = "아직 해소되지 않은(clearedAt 없음) 이벤트 전체를 발생시각 최신순으로 반환합니다. "
                    + "관제 화면 기동 시 현재 떠 있는 알람을 복원하는 용도입니다. "
                    + "해소됐지만 미확인인 알람까지 복구하려면 /history?completed=false 를 쓰세요."
    )
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<EventResponse>>> active() {
        return ResponseEntity.ok(ApiResponse.success("활성 이벤트 목록 조회", eventService.getActive()));
    }

    @Operation(
            summary = "이벤트 상세 조회",
            description = "이벤트 UUID로 단건 조회합니다.",
            parameters = {@Parameter(name = "id", description = "이벤트 ID(UUIDv7)", required = true)}
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("이벤트 상세 조회", eventService.get(id)));
    }
}
