package com.mpole.hana.digitaltwin.event.api;

import com.mpole.hana.digitaltwin.event.dto.EventResponse;
import com.mpole.hana.digitaltwin.event.dto.EventSimulationDtos.AckRequest;
import com.mpole.hana.digitaltwin.event.dto.EventSimulationDtos.AckResponse;
import com.mpole.hana.digitaltwin.event.dto.EventSimulationDtos.ScenarioOption;
import com.mpole.hana.digitaltwin.event.dto.EventSimulationDtos.SimulateRequest;
import com.mpole.hana.digitaltwin.event.dto.EventSimulationDtos.SimulateResponse;
import com.mpole.hana.digitaltwin.event.service.EventSimulationService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.global.security.JwtAuthenticationFilter;
import com.mpole.hana.digitaltwin.iam.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 모의 이벤트(SOP 시나리오 훈련) API — Unity 가 Ctrl+E 드롭다운에서 시나리오를 고르고 장비를 찍으면 호출한다. 2026-10-07.
 *
 * <p>실시간성이 없어 전부 REST 다. 발생·확인 모두 여기서 끝나고 event 서버·웹소켓은 관여하지 않는다
 * (event 서버는 {@code simulated} 규칙·행을 읽지 않는다). 다른 관제석에는 실시간으로 뜨지 않으며 복구 조회({@code /active},
 * {@code /api/event/history?completed=false}) 때 보인다. 해소(clear) 단계는 없고 확인(ack)이 곧 종료다.
 * 권한은 현재 다른 /api/** 와 같이 무인증 통과이며, 제한할 때는 SecurityConfig 의 주석(ADMIN·UNITY)을 푼다.
 * 자세한 흐름은 {@link EventSimulationService}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/event/simulate")
@RequiredArgsConstructor
@Tag(name = "EventSimulation", description = "모의 이벤트(SOP 시나리오 훈련) — 시나리오 목록·발생·확인·조회. 실제 이벤트와 분리된 REST 전용 경로")
public class EventSimulationController {

    private final EventSimulationService simulationService;
    private final UserRepository userRepository;

    @Operation(
            summary = "시나리오 목록 (Ctrl+E 드롭다운)",
            description = """
                    SOP·알람 정책에서 "모의 시나리오 전용" 으로 등록한 규칙 전부. `eventCode`(규칙 event_code, SCENARIO_…) 를 그대로 발생 요청에 보낸다.
                    `levels` 는 규칙 API 와 같은 레벨 객체(매칭값·아이콘·이펙트·outputs) 배열이라 고르는 순간 어떤 SOP·팝업이 뜰지 알 수 있다.
                    규칙의 활성 여부와 무관하게 전부 나온다(평가기가 안 읽는 규칙이라 활성이 의미 없음). event_code 가 없는 규칙은 제외.
                    """
    )
    @GetMapping("/scenarios")
    public ResponseEntity<ApiResponse<List<ScenarioOption>>> scenarios() {
        return ResponseEntity.ok(ApiResponse.success("시나리오 목록", simulationService.scenarios()));
    }

    @Hidden
    @Operation(
            summary = "시나리오 적용 장비 목록",
            description = "그 시나리오 규칙이 적용되는 장비 id. DEVICE 범위면 1개, CATEGORY 범위면 하위 소분류의 장비 전부. 클릭 가능한 장비만 켤 때 쓴다.",
            parameters = {@Parameter(name = "eventCode", description = "모의 전용 규칙 코드", example = "SCENARIO_FIRE_DETECT", required = true)}
    )
    @GetMapping("/devices")
    public ResponseEntity<ApiResponse<List<Long>>> devices(@RequestParam String eventCode) {
        return ResponseEntity.ok(ApiResponse.success("시나리오 적용 장비", simulationService.applicableDevices(eventCode)));
    }

    @Operation(
            summary = "모의 이벤트 발생",
            description = """
                    `eventCode` + `deviceId`(+ 선택 `level`) 로 tbl_event 에 simulated=true 행을 만들고 eventId 를 돌려준다.
                    해석: 코드가 정확히 일치하는 모의 전용 규칙 → 장비 적용 여부(DEVICE/CATEGORY) → 규칙 태그의 장비 포인트
                    → 레벨(생략 시 최고 심각도) → 값·outputs(SOP 템플릿)·icon·effect 스냅샷.
                    같은 규칙+장비+포인트에 열린 모의 행이 있으면 새로 만들지 않고 그 행을 `alreadyActive=true` 로 돌려준다.
                    응답의 `outputs.sop.templateId` 로 `GET /api/sop/templates/{id}` 를 불러 SOP 패널을 연다. 웹소켓 통지는 없다.
                    실패(400): eventCode 형식/규칙 없음 · 장비 없음 · 장비에 미적용 · 포인트 없음 · 레벨 없음 — message 에 사유.
                    """
    )
    @PostMapping
    public ResponseEntity<ApiResponse<SimulateResponse>> simulate(
            @RequestBody SimulateRequest request,
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal
    ) {
        SimulateResponse res = simulationService.simulate(request, resolveUserId(principal));
        return ResponseEntity.ok(ApiResponse.success(res.alreadyActive() ? "이미 열린 모의 이벤트" : "모의 이벤트 발생", res));
    }

    @Operation(
            summary = "모의 이벤트 확인(ack) = 종료",
            description = "acked_at/acked_by 를 기록하고 미해소면 cleared_at 도 같은 시각으로 닫는다(확인=종료, 실제와 같은 규칙. 해소 단계 없음). "
                    + "simulated 행만 대상이라 실제 이벤트 id 를 넣으면 400. 이미 확인된 건은 기존 값 그대로 `alreadyAcked=true`. "
                    + "본문 `{\"sopResult\": {…}}` — SOP 이벤트는 SOP 클릭 기록 스냅샷이 필수(없으면 400), SOP 가 아니면 생략·무시(2026-10-12).",
            parameters = {@Parameter(name = "id", description = "모의 이벤트 ID(UUIDv7)", required = true)}
    )
    @PostMapping("/{id}/ack")
    public ResponseEntity<ApiResponse<AckResponse>> ack(
            @PathVariable UUID id,
            @RequestBody(required = false) AckRequest request,
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal
    ) {
        AckResponse res = simulationService.ack(id, resolveUserId(principal), request == null ? null : request.sopResult());
        return ResponseEntity.ok(ApiResponse.success(res.alreadyAcked() ? "이미 확인된 모의 이벤트" : "모의 이벤트 확인(종료)", res));
    }

    @Operation(
            summary = "열린 모의 이벤트 목록",
            description = "cleared_at 이 없는 모의 이벤트 전체(최신순). Unity 재접속 시 훈련 상황 복구용. 전체 이력과 섞어 보려면 /api/event/history?simulated=true."
    )
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<EventResponse>>> active() {
        return ResponseEntity.ok(ApiResponse.success("열린 모의 이벤트", simulationService.active()));
    }

    /** loginId → user_id. 토큰에는 loginId 만 있어 simulated_by / acked_by(bigint) 로 변환한다. 비로그인(permitAll)이면 null. */
    private Long resolveUserId(JwtAuthenticationFilter.UserPrincipal principal) {
        if (principal == null || principal.loginId() == null) {
            return null;
        }
        return userRepository.findByLoginId(principal.loginId())
                .map(u -> u.getUserId())
                .orElse(null);
    }
}
