package com.mpole.hana.digitaltwin.memo.repository;

import com.mpole.hana.digitaltwin.memo.entity.Memo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoRepository extends JpaRepository<Memo, Long> {

    /**
     * 제목·본문 부분일치 검색.
     *
     * <p><b>{@code keyword} 에 null 을 넘기지 말 것.</b> 검색어가 없으면 서비스가
     * {@code findAll(pageable)} 로 분기한다.</p>
     *
     * <p>이유: {@code (:keyword IS NULL OR ... LIKE CONCAT('%', :keyword, '%'))} 형태로
     * 한 쿼리에 합치고 null 을 바인딩하면 파라미터 타입이 확정되지 않아
     * PostgreSQL 이 {@code ||} 를 bytea 연결로 해석하고
     * {@code lower(bytea) 이름의 함수가 없음} 으로 실패한다.
     * 캐스트를 붙여 우회할 수도 있으나, 분기하면 문제 자체가 생기지 않는다.</p>
     */
    @Query("""
            SELECT m FROM Memo m
            WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(COALESCE(m.content, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    Page<Memo> search(@Param("keyword") String keyword, Pageable pageable);
}
