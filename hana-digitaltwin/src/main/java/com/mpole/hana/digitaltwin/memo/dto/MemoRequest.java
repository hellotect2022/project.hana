package com.mpole.hana.digitaltwin.memo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 메모 등록·수정 요청.
 *
 * @param title   제목(필수, 200자 이내)
 * @param content 본문(선택, 길이 제한 없음)
 */
public record MemoRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이내로 입력하세요.")
        String title,

        String content
) {
}
