package com.mpole.hana.digitaltwin.command.repository;

import com.mpole.hana.digitaltwin.command.entity.CommandPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CommandPointRepository extends JpaRepository<CommandPoint, Long> {

    /**
     * 제어 포인트 전체 페이지 조회(관리 화면 — 장비 선택 없이 목록화). keyword(장비명/tagName/라벨) ·
     * categoryId(장비 소분류) 선택 필터. point/device 는 fetch join(to-one)이라 pageable 과 함께 안전.
     */
    @Query(value = """
            SELECT c FROM CommandPoint c
            JOIN FETCH c.point p
            JOIN FETCH c.device d
            WHERE (:keyword IS NULL OR :keyword = ''
                   OR lower(d.deviceName) LIKE lower(concat('%', :keyword, '%'))
                   OR lower(p.tagName)    LIKE lower(concat('%', :keyword, '%'))
                   OR lower(c.label)      LIKE lower(concat('%', :keyword, '%')))
              AND (:categoryId IS NULL OR d.category.categoryId = :categoryId)
            """,
            countQuery = """
            SELECT count(c) FROM CommandPoint c JOIN c.point p JOIN c.device d
            WHERE (:keyword IS NULL OR :keyword = ''
                   OR lower(d.deviceName) LIKE lower(concat('%', :keyword, '%'))
                   OR lower(p.tagName)    LIKE lower(concat('%', :keyword, '%'))
                   OR lower(c.label)      LIKE lower(concat('%', :keyword, '%')))
              AND (:categoryId IS NULL OR d.category.categoryId = :categoryId)
            """)
    Page<CommandPoint> search(@Param("keyword") String keyword,
                              @Param("categoryId") Long categoryId,
                              Pageable pageable);

    /** 특정 디바이스의 제어 포인트 목록 (관리 화면용). point 를 함께 로드해 응답 조립 시 N+1 방지. */
    @EntityGraph(attributePaths = {"point"})
    List<CommandPoint> findByDevice_DeviceIdOrderBySortOrderAsc(Long deviceId);

    /**
     * 디바이스 목록 임베드용 배치 조회 — 여러 디바이스의 활성 제어 포인트를 한 번에 로드한다.
     * <p>{@code point} 를 함께 fetch 해 tagName 접근 시 N+1 을 방지한다.
     * device 는 프록시로 두어도 식별자(device_id)는 초기화 없이 접근 가능하다.</p>
     */
    @EntityGraph(attributePaths = {"point"})
    List<CommandPoint> findByDevice_DeviceIdInAndActiveTrue(Collection<Long> deviceIds);
}
