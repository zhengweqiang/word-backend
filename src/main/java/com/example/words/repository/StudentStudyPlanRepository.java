package com.example.words.repository;

import com.example.words.model.StudentStudyPlan;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentStudyPlanRepository extends JpaRepository<StudentStudyPlan, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from StudentStudyPlan plan where plan.id = :id")
    Optional<StudentStudyPlan> findByIdForUpdate(@Param("id") Long id);

    List<StudentStudyPlan> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<StudentStudyPlan> findByStudyPlanIdOrderByStudentIdAsc(Long studyPlanId);

    List<StudentStudyPlan> findByStudyPlanIdAndStudentIdOrderByCreatedAtAsc(Long studyPlanId, Long studentId);
}
