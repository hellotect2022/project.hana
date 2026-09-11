package com.mpole.hana.digitaltwin.event.api;

import com.mpole.hana.common.constant.NotificationChannel;
import com.mpole.hana.digitaltwin.event.dto.EventActionResponse;
import com.mpole.hana.digitaltwin.event.dto.AlarmHistoryRow;
import com.mpole.hana.digitaltwin.event.dto.AlarmRuleOption;
import com.mpole.hana.digitaltwin.event.dto.EventPageResponse;
import com.mpole.hana.digitaltwin.event.dto.EventResponse;
import com.mpole.hana.digitaltwin.event.dto.EventDisplayResponse;
import com.mpole.hana.digitaltwin.event.service.EventService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.global.security.JwtAuthenticationFilter;
import com.mpole.hana.digitaltwin.iam.user.repository.UserRepository;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 이벤트 조회 + 확인(ack)/액션 완료 보고 API.
 *
 * <p>발생·해소 쓰기는 event-server(R2DBC)가 담당한다. 여기서는 조회와, 사람이 하는 두 가지 쓰기
 * — 팝업 확인(ack)과 채널 액션 완료 보고 — 만 제공한다.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/event")
@RequiredArgsConstructor
@Tag(name = "Event", description = "이벤트 조회 · 확인(ack) · 액션 완료 보고 API")
public class EventController {

    private final EventService eventService;
    private final UserRepository userRepository;

    @Operation(
            summary = "이벤트 목록 조회",
            description = "이벤트를 발생시각(occurredAt) 최신순으로 페이징 조회합니다. "
                    + "level/deviceId/active/from~to(occurredAt) 로 선택 필터링할 수 있습니다.",
            parameters = {
                    @Parameter(name = "level", description = "심각도 등급(CAUTION/WARNING/CRITICAL)", example = "CRITICAL"),
                    @Parameter(name = "deviceId", description = "장비 ID", example = "1001"),
                    @Parameter(name = "active", description = "조건 성립 중 여부(true=지금 떠 있는 알람만)", example = "true"),
                    @Parameter(name = "from", description = "발생시각 시작(ISO-8601, 예: 2026-07-01T00:00:00+09:00)"),
                    @Parameter(name = "to", description = "발생시각 종료(ISO-8601)"),
                    @Parameter(name = "page", description = "페이지 번호(0-base)", example = "0"),
                    @Parameter(name = "size", description = "페이지 크기", example = "20")
            }
    )
    @GetMapping
    public ResponseEntity<ApiResponse<EventPageResponse>> list(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        EventPageResponse result = eventService.getHistory(level, deviceId, active, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success("이벤트 목록 조회", result));
    }

    @Operation(
            summary = "알람 이력 조회 (공통 화면용)",
            description = """
                    알람 이력 화면(명칭·위치·알람명·완료여부·발생일시) 전용 조회입니다.
                    전력감시·엘리베이터·소방방재·태양광·출입통제·화장실 물리버튼 화면이 모두 이 API 를 씁니다.

                    · 명칭 = 장비명, 위치 = 장비 배치 층(조회 시 조인)
                    · 알람명 = 규칙 이름(ruleName)
                    · **완료 여부 = ackedAt 존재 여부** (조건 해소(active)가 아니라 사람의 조치 기준)
                    """,
            parameters = {
                    @Parameter(name = "categoryId", description = "장비 카테고리. 대·중분류를 넘기면 하위 소분류 전체로 확장", example = "12"),
                    @Parameter(name = "floorId", description = "층 ID(위치 필터)", example = "4"),
                    @Parameter(name = "ruleId", description = "알람명(규칙) ID", example = "7"),
                    @Parameter(name = "completed", description = "true=조치완료, false=미조치, 생략=전체", example = "false"),
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
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<AlarmHistoryRow> result = eventService.getAlarmHistory(
                level, ruleId, deviceId, floorId, categoryId, completed, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success("알람 이력 조회", result));
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
            description = "조건이 지금도 성립 중인(active=true) 이벤트 전체를 발생시각 최신순으로 반환합니다. "
                    + "관제 화면 기동 시 현재 떠 있는 알람을 복원하는 용도입니다."
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

    @Operation(
            summary = "이벤트 화면 조립 조회",
            description = "이벤트 UUID로 화면을 조립한다. 정본의 outputs 를 근거로 켜진 표시 채널만 반환: "
                    + "SOP(tbl_sop_template 규격 공급) + CCTV(발생 장비 주변 카메라). "
                    + "Unity 관제 클라이언트가 이 응답으로 SOP 패널과 CCTV 그리드를 표출한다.",
            parameters = {@Parameter(name = "id", description = "이벤트 ID(UUIDv7)", required = true)}
    )
    @GetMapping("/{id}/display")
    public ResponseEntity<ApiResponse<EventDisplayResponse>> display(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("이벤트 화면 조립", eventService.getDisplay(id)));
    }

    @Operation(
            summary = "이벤트 확인(ack)",
            description = "운전원이 팝업을 확인했음을 기록합니다. 최초 확인자·시각만 남고 재호출은 무시됩니다"
                    + "(응답 data=false). 확인은 조건 성립 여부(active)와 무관한 별도 축입니다.",
            parameters = {@Parameter(name = "id", description = "이벤트 ID(UUIDv7)", required = true)}
    )
    @PostMapping("/{id}/ack")
    public ResponseEntity<ApiResponse<Boolean>> ack(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal
    ) {
        boolean acked = eventService.ack(id, resolveUserId(principal));
        return ResponseEntity.ok(ApiResponse.success(acked ? "이벤트 확인 처리" : "이미 확인된 이벤트", acked));
    }

    @Operation(
            summary = "이벤트 액션 목록 조회",
            description = "해당 이벤트에서 발행된 채널별 액션(팝업/SOP/CCTV/SMS/이메일/제어)과 각 상태를 반환합니다.",
            parameters = {@Parameter(name = "id", description = "이벤트 ID(UUIDv7)", required = true)}
    )
    @GetMapping("/{id}/actions")
    public ResponseEntity<ApiResponse<List<EventActionResponse>>> actions(@PathVariable UUID id) {
        List<EventActionResponse> actions = eventService.getActions(id).stream()
                .map(EventActionResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("이벤트 액션 목록 조회", actions));
    }

    @Operation(
            summary = "이벤트 액션 완료 보고",
            description = "클라이언트(Unity/Web)가 채널 액션을 끝냈음을 보고합니다. 서버는 진행을 주관하지 않고 "
                    + "해당 액션 행을 DONE 으로 갱신하며 body 를 result 에 그대로 보관합니다. "
                    + "예: SOP 절차 완료 → channel=SOP.",
            parameters = {
                    @Parameter(name = "id", description = "이벤트 ID(UUIDv7)", required = true),
                    @Parameter(name = "channel", description = "채널(POPUP/SOP/CCTV/SMS/EMAIL/DEVICE_CONTROL)",
                            example = "SOP", required = true)
            }
    )
    @PostMapping("/{id}/actions/{channel}/complete")
    public ResponseEntity<ApiResponse<Void>> completeAction(
            @PathVariable UUID id,
            @PathVariable NotificationChannel channel,
            @RequestBody(required = false) Map<String, Object> result,
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal
    ) {
        eventService.completeAction(id, channel, result, resolveUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("이벤트 액션 완료 보고"));
    }

    /** loginId → user_id. 토큰에는 loginId 만 있어 acked_by/completed_by(bigint) 로 변환한다. */
    private Long resolveUserId(JwtAuthenticationFilter.UserPrincipal principal) {
        if (principal == null || principal.loginId() == null) {
            return null;
        }
        return userRepository.findByLoginId(principal.loginId())
                .map(u -> u.getUserId())
                .orElse(null);
    }
}
