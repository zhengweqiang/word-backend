package com.example.words.repository;

import com.example.words.model.StudentPaperAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentPaperAnswerRepository extends JpaRepository<StudentPaperAnswer, Long> {

    Optional<StudentPaperAnswer> findByAttemptIdAndReleaseQuestionId(Long attemptId, Long releaseQuestionId);

    List<StudentPaperAnswer> findByAttemptId(Long attemptId);
}
