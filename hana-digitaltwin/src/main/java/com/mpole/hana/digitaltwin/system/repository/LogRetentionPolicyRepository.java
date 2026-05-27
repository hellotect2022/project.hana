package com.mpole.hana.digitaltwin.system.repository;

import com.mpole.hana.digitaltwin.system.entity.LogRetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LogRetentionPolicyRepository extends JpaRepository<LogRetentionPolicy,Long> {

    Optional<LogRetentionPolicy> findByPolicyKey(String policyKey);

}
