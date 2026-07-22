package com.example.words.repository;

import com.example.words.model.StudentPointRule;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentPointRuleRepository extends JpaRepository<StudentPointRule, Long> {

    Optional<StudentPointRule> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rule from StudentPointRule rule where rule.code = :code")
    Optional<StudentPointRule> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rule from StudentPointRule rule where rule.id = :ruleId")
    Optional<StudentPointRule> findByIdForUpdate(@Param("ruleId") Long ruleId);

    List<StudentPointRule> findByEnabledTrue();
}
