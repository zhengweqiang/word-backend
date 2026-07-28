package com.example.words.repository;

import com.example.words.model.StudentPaperAttempt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentPaperAttemptRepository extends JpaRepository<StudentPaperAttempt, Long> {

    Optional<StudentPaperAttempt> findByPaperReleaseIdAndStudentId(Long paperReleaseId, Long studentId);

    List<StudentPaperAttempt> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<StudentPaperAttempt> findByPaperReleaseIdOrderByStudentIdAsc(Long paperReleaseId);
}
