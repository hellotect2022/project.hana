package com.mpole.hana.digitaltwin.space.repository;

import com.mpole.hana.digitaltwin.space.entity.ZoneDisplayPolicy;
import com.mpole.hana.digitaltwin.space.entity.ZoneSystemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ZoneDisplayPolicyRepository extends JpaRepository<ZoneDisplayPolicy, Long> {

    @Query("""
            SELECT p FROM ZoneDisplayPolicy p
            JOIN FETCH p.zone z
            JOIN FETCH z.floor f
            JOIN FETCH f.building b
            WHERE p.systemType = :systemType
            ORDER BY p.policyId
            """)
    List<ZoneDisplayPolicy> findAllBySystemTypeWithZone(@Param("systemType") ZoneSystemType systemType);

    boolean existsByZone_ZoneIdAndSystemType(Long zoneId, ZoneSystemType systemType);
}
