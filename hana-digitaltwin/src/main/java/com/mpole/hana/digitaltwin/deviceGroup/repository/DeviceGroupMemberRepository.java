package com.mpole.hana.digitaltwin.deviceGroup.repository;

import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DeviceGroupMemberRepository extends JpaRepository<DeviceGroupMember, Long> {

    /** 그룹 상세용. 장비·카테고리를 함께 끌어와 응답 조립 중 N+1 이 나지 않게 한다. */
    @Query("""
            select m from DeviceGroupMember m
              join fetch m.device d
              left join fetch d.category
            where m.group.groupId = :groupId
            order by m.sortOrder, m.memberId
            """)
    List<DeviceGroupMember> findByGroupIdWithDevice(@Param("groupId") Long groupId);

    /**
     * 목록 화면의 멤버수 배지용 — 그룹별 count 를 한 번에 가져온다.
     * 그룹마다 count 쿼리를 날리면 롤스크린 286건에서 그대로 N+1 이 된다.
     */
    @Query("""
            select m.group.groupId, count(m)
            from DeviceGroupMember m
            where m.group.groupId in :groupIds
            group by m.group.groupId
            """)
    List<Object[]> countByGroupIds(@Param("groupIds") Collection<Long> groupIds);

    /**
     * 이 장비들이 이미 속해 있는 <b>다른</b> 그룹 — 후보 목록에 같이 보여준다.
     *
     * <p>배타성 제약을 뺐기 때문에 중복 배정 자체는 막지 않는다. 대신 "이 모터는 이미
     * 3층 전체에 들어 있다"를 화면에서 보여줘야 실수로 두 부스에 같은 센서를 넣는 일을
     * 줄일 수 있다.</p>
     *
     * <p>반환: {@code [0]device_id · [1]group_code}</p>
     */
    @Query("""
            select m.device.deviceId, m.group.groupCode
            from DeviceGroupMember m
            where m.device.deviceId in :deviceIds
              and m.group.groupId <> :excludeGroupId
            order by m.group.groupCode
            """)
    List<Object[]> findOtherMemberships(@Param("deviceIds") Collection<Long> deviceIds,
                                        @Param("excludeGroupId") Long excludeGroupId);

    @Modifying
    @Query("delete from DeviceGroupMember m where m.group.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);
}
