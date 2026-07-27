package com.mpole.hana.digitaltwin.global.exception;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException e) {
        log.warn("인증 실패: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("인증에 실패했습니다.", "AUTHENTICATION_FAILED"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("접근 거부: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("접근 권한이 없습니다.", "ACCESS_DENIED"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String message = e.getMessage();
        log.warn("요청 검증 실패: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, "INVALID_REQUEST"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("유요하지 않은 필드값: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, "VALIDATION_ERROR"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage(), "INVALID_REQUEST"));
    }

    /**
     * 요청 본문 파싱 실패(잘못된 JSON, 알 수 없는 componentType/enum 등). 400으로 사유와 함께 안내.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        Throwable cause = e.getMostSpecificCause();
        String message = cause.getMessage() != null ? cause.getMessage() : "요청 본문을 읽을 수 없습니다.";
        log.warn("요청 본문 파싱 실패: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("요청 본문을 읽을 수 없습니다: " + message, "MALFORMED_REQUEST"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException e) {
        log.warn("처리 불가 상태: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage(), "INVALID_STATE"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("서버 오류 발생", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 내부 오류가 발생했습니다.", "INTERNAL_SERVER_ERROR"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> noResourceFoundException(NoResourceFoundException e) {
        log.error("요청하신 API 경로를 찾을 수 없습니다. {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("요청하신 API 경로를 찾을 수 없습니다. "+e.getMessage(), "NOT_FOUND"));
    }

    /**
     * DB 무결성 위반(FK/UNIQUE 등). Spring이 JPA 예외를 이 타입으로 변환한다.
     * 예: 다른 계통도/배선이 참조 중인 장비 삭제(FK RESTRICT) → 409로 명확히 안내.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        Throwable root = e.getMostSpecificCause();
        String rootMsg = root != null && root.getMessage() != null ? root.getMessage() : "";
        log.warn("데이터 무결성 위반: {}", rootMsg);

        String message;
        String code;
        String lower = rootMsg.toLowerCase();
        if (lower.contains("foreign key")) {
            message = "다른 항목(계통도/배선 등)에서 참조 중이라 삭제할 수 없습니다.";
            code = "FK_CONSTRAINT_VIOLATION";
        } else if (lower.contains("unique") || lower.contains("duplicate")) {
            message = "이미 존재하는 값입니다. (중복 제약 위반)";
            code = "UNIQUE_CONSTRAINT_VIOLATION";
        } else {
            message = "데이터 무결성 제약을 위반해 처리할 수 없습니다.";
            code = "DATA_INTEGRITY_VIOLATION";
        }
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(message, code));
    }

    /**
     * Hibernate 레벨의 제약 위반(flush 시점 등 변환 전에 노출되는 경우).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> constraintViolationException(ConstraintViolationException e) {
        log.warn("DB 제약 조건 위반: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("다른 항목에서 참조 중이거나 제약을 위반해 처리할 수 없습니다.", "CONSTRAINT_VIOLATION"));
    }

    /**
     * 토큰 만료. refresh 흐름에서 만료된 리프레시 토큰이 전파되면 여기로 온다.
     * 401 + REFRESH_TOKEN_EXPIRED 로 내려 프론트가 "세션 만료 → 재로그인"으로 분기할 수 있게 한다.
     */
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ApiResponse<Void>> expiredJwtException(ExpiredJwtException e) {
        log.warn("토큰 만료됨: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("리프레시 토큰이 만료되었습니다. 다시 로그인해 주세요.", "REFRESH_TOKEN_EXPIRED"));
    }
}
