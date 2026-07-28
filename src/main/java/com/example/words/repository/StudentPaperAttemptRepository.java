package com.example.words.repository;

import com.example.words.model.StudentPaperAttempt;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentPaperAttemptRepository extends JpaRepository<StudentPaperAttempt, Long> {

    Optional<StudentPaperAttempt> findByPaperReleaseIdAndStudentId(Long paperReleaseId, Long studentId);

    Optional<StudentPaperAttempt> findByIdAndStudentId(Long attemptId, Long studentId);

    @Query("SELECT attempt.paperReleaseId FROM StudentPaperAttempt attempt "
            + "WHERE attempt.id = :attemptId AND attempt.studentId = :studentId")
    Optional<Long> findPaperReleaseIdByIdAndStudentId(
            @Param("attemptId") Long attemptId, @Param("studentId") Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT attempt FROM StudentPaperAttempt attempt "
            + "WHERE attempt.id = :attemptId AND attempt.studentId = :studentId")
    Optional<StudentPaperAttempt> findByIdAndStudentIdForUpdate(
            @Param("attemptId") Long attemptId, @Param("studentId") Long studentId);

    List<StudentPaperAttempt> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<StudentPaperAttempt> findByPaperReleaseIdOrderByStudentIdAsc(Long paperReleaseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT attempt FROM StudentPaperAttempt attempt "
            + "WHERE attempt.paperReleaseId = :releaseId ORDER BY attempt.studentId")
    List<StudentPaperAttempt> findByPaperReleaseIdForUpdate(@Param("releaseId") Long releaseId);
}
