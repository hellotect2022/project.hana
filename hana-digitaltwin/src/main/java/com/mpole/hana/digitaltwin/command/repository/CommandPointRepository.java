package com.mpole.hana.digitaltwin.command.repository;

import com.mpole.hana.digitaltwin.command.entity.CommandPoint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CommandPointRepository extends JpaRepository<CommandPoint, Long> {

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
