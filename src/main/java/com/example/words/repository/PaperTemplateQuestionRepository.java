package com.example.words.repository;

import java.util.List;
import java.util.Optional;

import com.example.words.model.PaperTemplateQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaperTemplateQuestionRepository extends JpaRepository<PaperTemplateQuestion, Long> {

    List<PaperTemplateQuestion> findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(
            Long paperTemplateId);

    Optional<PaperTemplateQuestion> findByIdAndRemovedAtIsNull(Long id);

    @Query("SELECT MIN(question.questionOrder) FROM PaperTemplateQuestion question "
            + "WHERE question.paperTemplateId = :paperTemplateId")
    Integer findMinimumQuestionOrder(@Param("paperTemplateId") Long paperTemplateId);
}
