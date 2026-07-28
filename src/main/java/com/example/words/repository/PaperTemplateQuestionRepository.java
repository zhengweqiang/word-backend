package com.example.words.repository;

import com.example.words.model.PaperTemplateQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperTemplateQuestionRepository extends JpaRepository<PaperTemplateQuestion, Long> {

    List<PaperTemplateQuestion> findByPaperTemplateIdOrderByQuestionOrderAsc(Long paperTemplateId);
}
