package com.mpole.hana.digitaltwin.integration.emblaze.hqs;

import com.mpole.hana.digitaltwin.integration.emblaze.hqs.code.HqsResponseCode;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@link HqsApiController} 및 동일 패키지 내 REST 컨트롤러 예외를 HQS 응답 형식으로 변환.
 * <p>basePackageClasses 로 이 컨트롤러 패키지에만 한정한다(전역 핸들러와 분리).</p>
 */
@Slf4j
@RestControllerAdvice(basePackageClasses = HqsApiController.class)
public class HqsApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<HqsApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        log.debug("HQS validation failed: {}", ex.getMessage());
        return ResponseEntity.ok(HqsApiResponse.error(HqsResponseCode.REQUEST_INVALID_PARAM));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<HqsApiResponse<Void>> handleAny(Exception ex) {
        log.error("HQS API error", ex);
        return ResponseEntity.ok(HqsApiResponse.error(HqsResponseCode.SERVER_ERROR));
    }
}
