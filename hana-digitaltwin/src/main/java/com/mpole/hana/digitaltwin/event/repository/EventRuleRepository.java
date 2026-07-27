package com.mpole.hana.digitaltwin.event.repository;

import com.mpole.hana.digitaltwin.event.entity.EventRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EventRuleRepository extends JpaRepository<EventRule, Long> {

    List<EventRule> findAllByOrderByRuleNameAsc();

    List<EventRule> findByDeviceIdOrderByRuleNameAsc(Long deviceId);

    List<EventRule> findBySeverityOrderByRuleNameAsc(String severity);

    void deleteByDeviceId(Long deviceId);

    void deleteByDeviceIdIn(Collection<Long> deviceIds);
}
