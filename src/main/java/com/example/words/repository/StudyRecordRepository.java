package com.example.words.repository;

import com.example.words.model.StudyRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyRecordRepository extends JpaRepository<StudyRecord, Long> {

    List<StudyRecord> findByStudentStudyPlanIdAndTaskDate(Long studentStudyPlanId, LocalDate taskDate);

    Optional<StudyRecord> findByRequestKey(String requestKey);

    @Query(value = """
            SELECT record.id AS sourceId,
                   plan.student_id AS studentId
              FROM study_records record
             JOIN student_study_plans plan ON plan.id = record.student_study_plan_id
             WHERE record.result = 'CORRECT'
               AND record.points_eligible = TRUE
               AND NOT EXISTS (
                   SELECT 1
                     FROM student_point_events event
                    WHERE event.idempotency_key =
                          'study-record:' || record.id || ':correct:STUDY_RECORD_CORRECT'
               )
             ORDER BY record.id
             LIMIT :batchSize
            """, nativeQuery = true)
    List<MissingCorrectPointSource> findMissingCorrectPointSources(@Param("batchSize") int batchSize);

    interface MissingCorrectPointSource {

        Long getSourceId();

        Long getStudentId();
    }
}
