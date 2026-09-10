package com.mpole.hana.digitaltwin.event.repository;

import com.mpole.hana.digitaltwin.event.entity.RuleScope;
import com.mpole.hana.digitaltwin.event.entity.EventRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EventRuleRepository extends JpaRepository<EventRule, Long> {

    List<EventRule> findAllByOrderByRuleIdDesc();

    List<EventRule> findByScopeOrderByRuleIdDesc(RuleScope scope);

    List<EventRule> findByActiveTrueOrderByRuleIdDesc();

    /** 장비 삭제 시 그 장비에 걸린 규칙(scope=DEVICE)도 함께 제거된다. */
    void deleteByDeviceId(Long deviceId);

    void deleteByDeviceIdIn(Collection<Long> deviceIds);
}
