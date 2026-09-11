package com.mpole.hana.digitaltwin.integration.emblaze.hqs;

import com.mpole.hana.digitaltwin.integration.emblaze.hqs.code.HqsResponseCode;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
@RestControllerAdvice(basePackages = "com.mpole.hana.digitaltwin.integration.emblaze.hqs")
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
