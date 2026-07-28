package com.example.words.repository;

import com.example.words.model.Exam;
import com.example.words.model.ExamStatus;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findByStatusOrderBySubmittedAtDescCreatedAtDesc(ExamStatus status);

    List<Exam> findByDictionaryIdAndStatusOrderBySubmittedAtDescCreatedAtDesc(Long dictionaryId, ExamStatus status);

    @Query("SELECT exam FROM Exam exam "
            + "WHERE exam.targetUserId = :studentId "
            + "AND exam.status IN :statuses")
    List<Exam> findStudentAssessments(
            @Param("studentId") Long studentId,
            @Param("statuses") Set<ExamStatus> statuses);
}
