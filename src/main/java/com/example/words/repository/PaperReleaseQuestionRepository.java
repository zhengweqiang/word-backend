package com.example.words.repository;

import com.example.words.model.PaperReleaseQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperReleaseQuestionRepository extends JpaRepository<PaperReleaseQuestion, Long> {

    List<PaperReleaseQuestion> findByPaperReleaseIdOrderByQuestionOrderAsc(Long paperReleaseId);
}
