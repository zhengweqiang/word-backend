package com.example.words.repository;

import com.example.words.model.StudentPointRuleAudit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentPointRuleAuditRepository extends JpaRepository<StudentPointRuleAudit, Long> {

    List<StudentPointRuleAudit> findByRuleIdOrderByCreatedAtDesc(Long ruleId);
}
