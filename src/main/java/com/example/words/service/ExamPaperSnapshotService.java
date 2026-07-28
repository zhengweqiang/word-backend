package com.example.words.service;

import com.example.words.model.PaperReleaseQuestion;
import com.example.words.model.PaperTemplateQuestion;
import com.example.words.model.QuestionBankItem;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class ExamPaperSnapshotService {

    public PaperTemplateQuestion createTemplateQuestionSnapshot(
            QuestionBankItem question, Long paperTemplateId, Integer questionOrder, BigDecimal score) {
        PaperTemplateQuestion snapshot = new PaperTemplateQuestion();
        snapshot.setPaperTemplateId(paperTemplateId);
        snapshot.setSourceQuestionId(question.getId());
        snapshot.setQuestionOrder(questionOrder);
        snapshot.setQuestionType(question.getQuestionType());
        snapshot.setStem(question.getStem());
        snapshot.setOptionsJson(question.getOptionsJson());
        snapshot.setAcceptedAnswersJson(question.getAcceptedAnswersJson());
        snapshot.setExplanation(question.getExplanation());
        snapshot.setScore(score);
        snapshot.setDictionaryId(question.getDictionaryId());
        snapshot.setMetaWordId(question.getMetaWordId());
        return snapshot;
    }

    public PaperReleaseQuestion createReleaseQuestionSnapshot(PaperTemplateQuestion question, Long paperReleaseId) {
        PaperReleaseQuestion snapshot = new PaperReleaseQuestion();
        snapshot.setPaperReleaseId(paperReleaseId);
        snapshot.setPaperTemplateQuestionId(question.getId());
        snapshot.setSourceQuestionId(question.getSourceQuestionId());
        snapshot.setQuestionOrder(question.getQuestionOrder());
        snapshot.setQuestionType(question.getQuestionType());
        snapshot.setStem(question.getStem());
        snapshot.setOptionsJson(question.getOptionsJson());
        snapshot.setAcceptedAnswersJson(question.getAcceptedAnswersJson());
        snapshot.setExplanation(question.getExplanation());
        snapshot.setScore(question.getScore());
        snapshot.setDictionaryId(question.getDictionaryId());
        snapshot.setMetaWordId(question.getMetaWordId());
        return snapshot;
    }
}
