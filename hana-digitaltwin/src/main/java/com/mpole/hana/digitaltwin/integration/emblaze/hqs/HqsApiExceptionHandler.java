package com.mpole.hana.digitaltwin.integration.emblaze.hqs;

import com.mpole.hana.digitaltwin.command.exception.DeviceControlException;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.code.HqsResponseCode;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * HQS 연동 REST 컨트롤러 예외를 HQS 응답 형식({@code code/message/timestamp/data})으로 변환.
 *
 * <p><b>범위를 {@code hqs} 패키지 전체로 잡는다.</b> 도메인별로 하위 패키지(`wc`, `charger`,
 * `screen`)를 나눴기 때문에, 예전처럼 {@code basePackageClasses = HqsApiController.class} 로 두면
 * 그 컨트롤러가 있는 {@code wc} 만 걸리고 <b>나머지 도메인은 전역 핸들러로 새어나가</b>
 * HQS 가 기대하는 봉투가 아닌 응답을 받게 된다.</p>
 *
 * <p>전역 핸들러와는 이 패키지 경계로 분리된다.</p>
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.mpole.hana.digitaltwin.integration.emblaze.hqs")
public class HqsApiExceptionHandler {

    /**
     * 요청 본문 검증 실패({@code @NotBlank} · {@code @Min} 등).
     *
     * <p><b>어느 필드가 왜 틀렸는지</b>를 message 에 담는다. 고정 문구만 주면 HQS 가
     * 무엇을 고쳐야 할지 알 수 없어 서로 로그를 대조해야 한다.</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<HqsApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("HQS 요청 검증 실패: {}", detail);
        return ResponseEntity.ok(withDetail(HqsResponseCode.REQUEST_INVALID_PARAM, detail));
    }

    /**
     * 잘못된 요청값 — 없는 층·없는 제어기, 허용 범위·단위를 벗어난 값 등.
     *
     * <p>{@code DeviceControlService.resolveSendValue} 가 던지는
     * "입력 단위에 맞지 않습니다. value=55, step=20.0" 같은 메시지가 여기로 온다.
     * 그대로 실어 보내야 HQS 가 무엇이 문제인지 알 수 있다.</p>
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<HqsApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("HQS 요청 오류: {}", ex.getMessage());
        return ResponseEntity.ok(withDetail(HqsResponseCode.REQUEST_INVALID_PARAM, ex.getMessage()));
    }

    /**
     * 우리 쪽 설정이 덜 된 상태 — 제어 포인트에 SI 매핑이 없다, RANGE 인데 min/max 가 없다 등.
     * <p>요청은 정상인데 서버 설정 때문에 못 하는 경우라 요청 오류(IF110)와 구분한다.</p>
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<HqsApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.error("HQS 설정 오류: {}", ex.getMessage());
        return ResponseEntity.ok(withDetail(HqsResponseCode.BUSINESS_INCORRECT_SETTING, ex.getMessage()));
    }

    /** 외부(XN) 연계가 제어를 받지 못했을 때. 요청·설정은 정상이고 연계 구간이 실패한 것이다. */
    @ExceptionHandler(DeviceControlException.class)
    public ResponseEntity<HqsApiResponse<Void>> handleDeviceControl(DeviceControlException ex) {
        log.error("HQS 제어 연계 실패: {}", ex.getMessage());
        return ResponseEntity.ok(withDetail(HqsResponseCode.API_FAILED_PROCESS, ex.getMessage()));
    }

    /**
     * 그 밖의 모든 예외.
     *
     * <p><b>여기만 상세를 숨긴다.</b> 예상하지 못한 예외의 메시지에는 내부 구조(SQL·클래스명 등)가
     * 섞일 수 있어 외부에 그대로 내보내지 않는다. 원인은 서버 로그에 남는다.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<HqsApiResponse<Void>> handleAny(Exception ex) {
        log.error("HQS API error", ex);
        return ResponseEntity.ok(HqsApiResponse.error(HqsResponseCode.SERVER_ERROR));
    }

    /**
     * 코드는 유지하고 <b>사유만 덧붙인다.</b>
     * <p>HQS 는 {@code code} 로 분기하므로 코드 체계는 그대로 두고, 사람이 읽을 사유를
     * message 뒤에 붙인다. 사유가 비면 기존 문구만 나간다.</p>
     */
    private static HqsApiResponse<Void> withDetail(HqsResponseCode code, String detail) {
        if (detail == null || detail.isBlank()) {
            return HqsApiResponse.error(code);
        }
        return HqsApiResponse.error(code.getCode(), code.getMessage() + " (" + detail + ")");
    }
}
