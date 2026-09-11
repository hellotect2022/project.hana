package com.mpole.hana.digitaltwin.memo.dto;

import com.mpole.hana.digitaltwin.memo.entity.Memo;

import java.time.OffsetDateTime;

/**
 * 메모 응답.
 *
 * @param createdAt 최초 작성 일시
 * @param updatedAt 마지막 수정 일시 — 목록 정렬 기준
 */
public record MemoResponse(
        Long memoId,
        String title,
        String content,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static MemoResponse from(Memo memo) {
        return new MemoResponse(
                memo.getMemoId(),
                memo.getTitle(),
                memo.getContent(),
                memo.getCreatedAt(),
                memo.getUpdatedAt()
        );
    }
}
