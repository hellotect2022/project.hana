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

    /** 모의 시나리오 전용 규칙 전부 — Unity 훈련 드롭다운. 활성 여부 무관(평가기가 안 읽으므로 active 는 의미 없음). 2026-10-07 */
    List<EventRule> findBySimulatedTrueOrderByRuleIdDesc();

    /** 시나리오 코드(event_code, SCENARIO_…)로 모의 전용 규칙 조회. 코드는 유니크가 아니라 목록이며 호출부가 최신 1건을 쓴다. 2026-10-07 */
    List<EventRule> findByEventCodeAndSimulatedTrueOrderByRuleIdDesc(String eventCode);

    /** 장비 삭제 시 그 장비에 걸린 규칙(scope=DEVICE)도 함께 제거된다. */
    void deleteByDeviceId(Long deviceId);

    void deleteByDeviceIdIn(Collection<Long> deviceIds);
}
