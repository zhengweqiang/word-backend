package com.example.words.repository;

import com.example.words.model.StudyDayTask;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyDayTaskRepository extends JpaRepository<StudyDayTask, Long> {

    List<StudyDayTask> findByStudentStudyPlanIdAndTaskDateOrderByCreatedAtAsc(
            Long studentStudyPlanId,
            LocalDate taskDate);

    List<StudyDayTask> findByStudentStudyPlanIdAndTaskDateBeforeOrderByTaskDateAsc(
            Long studentStudyPlanId,
            LocalDate taskDate);

    @Query(value = """
            SELECT task.id AS sourceId,
                   plan.student_id AS studentId
              FROM study_day_tasks task
             JOIN student_study_plans plan ON plan.id = task.student_study_plan_id
             WHERE task.status = 'COMPLETED'
               AND task.points_eligible = TRUE
               AND (task.new_count + task.review_count + task.overdue_count) > 0
               AND task.completed_count >= (task.new_count + task.review_count + task.overdue_count)
               AND NOT EXISTS (
                   SELECT 1
                     FROM student_point_events event
                    WHERE event.idempotency_key =
                          'study-day-task:' || task.id || ':completed:DAILY_TASK_COMPLETED'
               )
             ORDER BY task.id
             LIMIT :batchSize
            """, nativeQuery = true)
    List<MissingCompletedPointSource> findMissingCompletedPointSources(@Param("batchSize") int batchSize);

    interface MissingCompletedPointSource {

        Long getSourceId();

        Long getStudentId();
    }
}
