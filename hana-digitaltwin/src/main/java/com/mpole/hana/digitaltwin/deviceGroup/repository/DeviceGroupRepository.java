package com.mpole.hana.digitaltwin.deviceGroup.repository;

import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceGroupRepository extends JpaRepository<DeviceGroup, Long> {

    boolean existsByGroupCode(String groupCode);

    /** 외부(HQS)가 group_code 로 그룹을 지목할 때 쓴다. */
    Optional<DeviceGroup> findByGroupCode(String groupCode);

    /** 실제로 쓰이고 있는 용도 코드 — 화면 select 가 enum 에 없는 값도 보여줄 수 있게 한다. */
    @Query("select distinct g.groupType from DeviceGroup g order by g.groupType")
    List<String> findDistinctGroupTypes();

    /**
     * 그룹 검색.
     *
     * <p><b>{@code :param IS NULL} 로 선택필터를 표현하지 않는다</b> — 이 프로젝트에서 그 패턴이
     * PostgreSQL 타입추론 오류를 낸 전례가 있다(realtime/alarm 조회에서 CAST 로 우회).
     * 그래서 선택필터를 두 가지 방식으로 푼다.</p>
     *
     * <ul>
     *   <li>{@code keyword} — 빈 값이면 {@code %} 로 만들어 전건을 통과시킨다.</li>
     *   <li>{@code actives} — "전체"를 {@code null} 이 아니라 <b>{@code [true, false]}</b> 로
     *       표현한다. 파라미터가 항상 값을 가지므로 분기도 타입추론 문제도 없다.</li>
     *   <li>{@code groupType} — 이 방법이 안 통해서(용도 목록이 열려 있다) 메서드를 나눈다.</li>
     * </ul>
     */
    @Query("""
            select g from DeviceGroup g
            where g.groupType = :groupType
              and g.active in :actives
              and (lower(g.groupCode) like :keyword or lower(g.groupName) like :keyword)
            """)
    Page<DeviceGroup> searchByType(@Param("groupType") String groupType,
                                   @Param("keyword") String keyword,
                                   @Param("actives") Collection<Boolean> actives,
                                   Pageable pageable);

    @Query("""
            select g from DeviceGroup g
            where g.active in :actives
              and (lower(g.groupCode) like :keyword or lower(g.groupName) like :keyword)
            """)
    Page<DeviceGroup> search(@Param("keyword") String keyword,
                             @Param("actives") Collection<Boolean> actives,
                             Pageable pageable);
}
