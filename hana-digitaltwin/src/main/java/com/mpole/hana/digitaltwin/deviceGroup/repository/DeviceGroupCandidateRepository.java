package com.mpole.hana.digitaltwin.deviceGroup.repository;

import com.mpole.hana.digitaltwin.device.entity.Device;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 그룹에 넣을 후보 장비 조회 전용.
 *
 * <p>{@code Device} 를 읽지만 {@code device} 패키지의 기존 리포지토리를 건드리지 않기 위해
 * 별도 인터페이스로 둔다. {@code JpaRepository} 가 아니라 {@code Repository} 를 상속해
 * 쓰기 메서드를 아예 노출하지 않는다 — 여기서 장비를 수정할 일은 없다.</p>
 *
 * <p><b>다른 그룹에 이미 속한 장비를 제외하지 않는다.</b> 배타성 제약을 뺐기 때문에
 * 같은 장비가 여러 그룹에 속하는 것이 정상이다(모터 한 대가 "3층 전체"와 "3층 서쪽"에
 * 동시 소속). 대신 서비스가 기존 소속 그룹을 함께 실어 화면에서 보이게 한다.</p>
 */
public interface DeviceGroupCandidateRepository extends Repository<Device, Long> {

    /**
     * 멤버 저장 시 id 로 장비 실체를 집어 온다.
     *
     * <p>{@code getReferenceById} 프록시로는 역할 추정에 필요한 카테고리를 읽을 수 없어
     * 실체를 가져온다. 카테고리는 함께 fetch 해 N+1 을 막는다.</p>
     */
    @Query("""
            select d from Device d
              left join fetch d.category
            where d.deviceId in :deviceIds
            """)
    List<Device> findAllByIds(@Param("deviceIds") Collection<Long> deviceIds);

    /**
     * 후보 장비.
     *
     * <p>이미 <b>그 그룹에</b> 들어 있는 장비만 뺀다 ({@code uk_dgm_group_device} 위반 방지).
     * 다른 그룹 소속은 빼지 않는다.</p>
     */
    @Query("""
            select d from Device d
              left join fetch d.category c
            where d.deviceId not in (
                    select m.device.deviceId from DeviceGroupMember m where m.group.groupId = :groupId)
              and (lower(d.deviceName) like :keyword
                   or lower(d.deviceKey) like :keyword
                   or lower(coalesce(d.deviceDisplayName, '')) like :keyword)
            order by d.deviceName
            """)
    List<Device> findCandidates(@Param("groupId") Long groupId,
                                @Param("keyword") String keyword,
                                Pageable pageable);

    /** 소분류 카테고리로 좁힌 후보. {@code :param IS NULL} 패턴을 피하려고 메서드를 나눈다. */
    @Query("""
            select d from Device d
              left join fetch d.category c
            where d.deviceId not in (
                    select m.device.deviceId from DeviceGroupMember m where m.group.groupId = :groupId)
              and c.categoryId = :categoryId
              and (lower(d.deviceName) like :keyword
                   or lower(d.deviceKey) like :keyword
                   or lower(coalesce(d.deviceDisplayName, '')) like :keyword)
            order by d.deviceName
            """)
    List<Device> findCandidatesByCategory(@Param("groupId") Long groupId,
                                          @Param("categoryId") Long categoryId,
                                          @Param("keyword") String keyword,
                                          Pageable pageable);
}
