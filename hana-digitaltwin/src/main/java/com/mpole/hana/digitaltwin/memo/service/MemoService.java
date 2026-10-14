package com.mpole.hana.digitaltwin.memo.service;

import com.mpole.hana.digitaltwin.memo.dto.MemoRequest;
import com.mpole.hana.digitaltwin.memo.dto.MemoResponse;
import com.mpole.hana.digitaltwin.memo.entity.Memo;
import com.mpole.hana.digitaltwin.memo.repository.MemoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoService {

    private final MemoRepository memoRepository;

    @Transactional(readOnly = true)
    public Page<MemoResponse> getMemos(String keyword, Pageable pageable) {
        String normalized = trimToNull(keyword);
        // 검색어가 없으면 전체 조회로 분기한다 — search() 에 null 을 넘기면
        // 파라미터 타입 미확정으로 PostgreSQL 이 lower(bytea) 를 찾다가 실패한다.
        Page<Memo> page = (normalized == null)
                ? memoRepository.findAll(pageable)
                : memoRepository.search(normalized, pageable);
        return page.map(MemoResponse::from);
    }

    @Transactional(readOnly = true)
    public MemoResponse getMemo(Long memoId) {
        return MemoResponse.from(findOrThrow(memoId));
    }

    @Transactional
    public MemoResponse createMemo(MemoRequest request) {
        Memo memo = memoRepository.save(Memo.builder()
                .title(request.title().trim())
                .content(request.content())
                .build());
        log.info("메모 등록 memoId={} title={}", memo.getMemoId(), memo.getTitle());
        return MemoResponse.from(memo);
    }

    @Transactional
    public MemoResponse updateMemo(Long memoId, MemoRequest request) {
        Memo memo = findOrThrow(memoId);
        memo.setTitle(request.title().trim());
        memo.setContent(request.content());
        // 더티 체킹으로 UPDATE — @PreUpdate 가 updated_at 을 갱신한다.
        log.info("메모 수정 memoId={}", memoId);
        return MemoResponse.from(memo);
    }

    @Transactional
    public void deleteMemo(Long memoId) {
        Memo memo = findOrThrow(memoId);
        memoRepository.delete(memo);
        log.info("메모 삭제 memoId={}", memoId);
    }

    private Memo findOrThrow(Long memoId) {
        return memoRepository.findById(memoId)
                .orElseThrow(() -> new IllegalArgumentException("메모를 찾을 수 없습니다. memoId=" + memoId));
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
