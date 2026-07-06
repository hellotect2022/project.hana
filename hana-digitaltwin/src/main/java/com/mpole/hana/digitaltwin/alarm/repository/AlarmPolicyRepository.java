package com.mpole.hana.digitaltwin.alarm.repository;

import com.mpole.hana.digitaltwin.alarm.entity.AlarmPolicy;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlarmPolicyRepository extends JpaRepository<AlarmPolicy, Long> {

    List<AlarmPolicy> findAllByOrderByPolicyIdDesc();

    List<AlarmPolicy> findByScopeOrderByPolicyIdDesc(AlarmScope scope);

    List<AlarmPolicy> findByActiveTrueOrderByPolicyIdDesc();
}
