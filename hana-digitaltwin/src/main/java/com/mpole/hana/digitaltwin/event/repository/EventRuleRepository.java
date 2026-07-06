package com.mpole.hana.digitaltwin.event.repository;

import com.mpole.hana.digitaltwin.event.entity.EventRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventRuleRepository extends JpaRepository<EventRule, Long> {

    List<EventRule> findAllByOrderByRuleNameAsc();

    List<EventRule> findByDeviceIdOrderByRuleNameAsc(Long deviceId);

    List<EventRule> findBySeverityOrderByRuleNameAsc(String severity);

    @Query("""
            SELECT r FROM EventRule r
            LEFT JOIN FETCH r.sopTemplate
            WHERE r.ruleId = :ruleId
            """)
    Optional<EventRule> findByIdWithSopTemplate(@Param("ruleId") Long ruleId);

    void deleteByDeviceId(Long deviceId);

    void deleteByDeviceIdIn(Collection<Long> deviceIds);
}
