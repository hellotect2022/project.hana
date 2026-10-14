package com.mpole.hana.digitaltwin.external.hqs.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * HQS 연동 응답 코드 (외부 업체 스펙).
 */
@Getter
@RequiredArgsConstructor
public enum HqsResponseCode {

    SUCCESS("IF000", "정상 처리 되었습니다."),
    REQUEST_INVALID_PARAM("IF110", "요청 파라미터가 올바르지 않습니다."),
    REQUEST_REQUIRED_MIN_CNT("IF120", "요청 파라미터의 개수가 부족합니다."),
    REQUEST_REQUIRED_MAX_CNT("IF130", "요청 파라미터의 개수가 초과되었습니다."),
    REQUEST_FAILED_PROCESS("IF190", "요청 처리 중 오류가 발생했습니다."),
    REQUEST_DUP_EXISTS("IF191", "요청 데이터가 중복 되었습니다."),
    AUTH_INVALID_INFO("IF210", "인증 정보가 일치하지 않습니다."),
    AUTH_INVALID_PASSWORD("IF212", "비밀번호가 일치하지 않습니다."),
    AUTH_INFO_NOT_EXIST("IF220", "인증 정보가 존재하지 않습니다."),
    AUTH_OVER_TCNT("IF230", "인증 횟수를 초과하였습니다."),
    AUTH_EXPIRED("IF240", "인증이 만료되었습니다."),
    AUTH_EXPIRE_TOKEN("IF241", "이미 만료된 토큰입니다."),
    AUTH_BLACKLIST_TOKEN("IF242", "다시 로그인해주세요."),
    AUTH_INVALID_TOKEN("IF243", "인증정보가 올바르지 않거나 만료되었습니다. 다시 로그인해주세요."),
    AUTH_FAILED_PROCESS("IF290", "인증 처리 중 오류가 발생했습니다."),
    BUSINESS_INCORRECT_SETTING("IF310", "서비스 정보 설정 중 오류가 발생했습니다."),
    BUSINESS_DATA_NOT_EXIST("IF320", "데이터가 존재하지 않습니다."),
    BUSINESS_OVER_MAX_SIZE("IF330", "데이터 최대 개수를 초과하였습니다."),
    BUSINESS_DATA_EXPIRED("IF340", "데이터가 만료되었습니다."),
    BUSINESS_NOT_ALLOWED_EDIT("IF350", "수정 권한이 없습니다."),
    BUSINESS_NOT_ALLOWED_CONT("IF360", "질문 권한이 없습니다."),
    BUSINESS_DATA_DUPLICATION("IF370", "데이터가 중복되었습니다."),
    BUSINESS_NOT_SUPPORTED("IF380", "지원하지 않는 기능입니다."),
    BUSINESS_FAILED_PROCESS("IF390", "서비스 처리 중 오류가 발생했습니다."),
    API_INVALID_REQUEST("IF510", "연계 요청 중 오류가 발생했습니다."),
    API_INVALID_RESPONSE("IF520", "연계 응답이 올바르지 않습니다."),
    API_FAILED_PROCESS("IF590", "연계 처리 중 오류가 발생했습니다."),
    SERVER_ERROR("IF999", "예기치 않은 오류가 발생하였습니다.");

    private final String code;
    private final String message;
}
