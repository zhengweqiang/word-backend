package com.example.words.service;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.words.model.PaperReleaseQuestion;
import com.example.words.model.PaperTemplateQuestion;
import com.example.words.model.QuestionBankItem;
import com.example.words.model.QuestionType;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExamPaperSnapshotServiceTest {

    private final ExamPaperSnapshotService snapshotService = new ExamPaperSnapshotService();

    @Test
    void templateQuestionSnapshotDoesNotChangeWhenSourceQuestionMutates() {
        QuestionBankItem source = question();

        PaperTemplateQuestion snapshot = snapshotService.createTemplateQuestionSnapshot(
                source, 10L, 3, new BigDecimal("2.50"));
        source.setStem("changed stem");
        source.setOptionsJson("[\"Z\"]");
        source.setAcceptedAnswersJson("[\"Z\"]");
        source.setExplanation("changed explanation");
        source.setDictionaryId(99L);
        source.setMetaWordId(98L);

        assertAll(
                () -> assertEquals(10L, snapshot.getPaperTemplateId()),
                () -> assertEquals(41L, snapshot.getSourceQuestionId()),
                () -> assertEquals(3, snapshot.getQuestionOrder()),
                () -> assertEquals(QuestionType.SINGLE_CHOICE, snapshot.getQuestionType()),
                () -> assertEquals("original stem", snapshot.getStem()),
                () -> assertEquals("[\"A\",\"B\"]", snapshot.getOptionsJson()),
                () -> assertEquals("[\"A\"]", snapshot.getAcceptedAnswersJson()),
                () -> assertEquals("original explanation", snapshot.getExplanation()),
                () -> assertEquals(new BigDecimal("2.50"), snapshot.getScore()),
                () -> assertEquals(11L, snapshot.getDictionaryId()),
                () -> assertEquals(12L, snapshot.getMetaWordId())
        );
    }

    @Test
    void releaseQuestionSnapshotDoesNotChangeWhenTemplateQuestionMutates() {
        PaperTemplateQuestion source = templateQuestion();

        PaperReleaseQuestion snapshot = snapshotService.createReleaseQuestionSnapshot(source, 20L);
        source.setStem("changed stem");
        source.setOptionsJson("[\"Z\"]");
        source.setAcceptedAnswersJson("[\"Z\"]");
        source.setExplanation("changed explanation");
        source.setScore(BigDecimal.TEN);
        source.setDictionaryId(99L);
        source.setMetaWordId(98L);

        assertAll(
                () -> assertEquals(20L, snapshot.getPaperReleaseId()),
                () -> assertEquals(31L, snapshot.getPaperTemplateQuestionId()),
                () -> assertEquals(41L, snapshot.getSourceQuestionId()),
                () -> assertEquals(3, snapshot.getQuestionOrder()),
                () -> assertEquals(QuestionType.SINGLE_CHOICE, snapshot.getQuestionType()),
                () -> assertEquals("original stem", snapshot.getStem()),
                () -> assertEquals("[\"A\",\"B\"]", snapshot.getOptionsJson()),
                () -> assertEquals("[\"A\"]", snapshot.getAcceptedAnswersJson()),
                () -> assertEquals("original explanation", snapshot.getExplanation()),
                () -> assertEquals(new BigDecimal("2.50"), snapshot.getScore()),
                () -> assertEquals(11L, snapshot.getDictionaryId()),
                () -> assertEquals(12L, snapshot.getMetaWordId())
        );
    }

    private QuestionBankItem question() {
        QuestionBankItem question = new QuestionBankItem();
        question.setId(41L);
        question.setQuestionType(QuestionType.SINGLE_CHOICE);
        question.setStem("original stem");
        question.setOptionsJson("[\"A\",\"B\"]");
        question.setAcceptedAnswersJson("[\"A\"]");
        question.setExplanation("original explanation");
        question.setDictionaryId(11L);
        question.setMetaWordId(12L);
        return question;
    }

    private PaperTemplateQuestion templateQuestion() {
        PaperTemplateQuestion question = new PaperTemplateQuestion();
        question.setId(31L);
        question.setSourceQuestionId(41L);
        question.setQuestionOrder(3);
        question.setQuestionType(QuestionType.SINGLE_CHOICE);
        question.setStem("original stem");
        question.setOptionsJson("[\"A\",\"B\"]");
        question.setAcceptedAnswersJson("[\"A\"]");
        question.setExplanation("original explanation");
        question.setScore(new BigDecimal("2.50"));
        question.setDictionaryId(11L);
        question.setMetaWordId(12L);
        return question;
    }
}
