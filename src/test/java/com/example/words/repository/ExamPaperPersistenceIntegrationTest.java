package com.example.words.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.words.model.PaperBlankAnswerPolicy;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperReleaseQuestion;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.PaperReleaseTarget;
import com.example.words.model.PaperResultVisibility;
import com.example.words.model.PaperTemplate;
import com.example.words.model.PaperTemplateQuestion;
import com.example.words.model.QuestionBankItem;
import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.QuestionImportBatch;
import com.example.words.model.QuestionImportPreviewRow;
import com.example.words.model.QuestionImportPreviewRowStatus;
import com.example.words.model.QuestionType;
import com.example.words.model.StudentPaperAnswer;
import com.example.words.model.StudentPaperAttempt;
import com.example.words.model.StudentPaperAttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ExamPaperPersistenceIntegrationTest {

    @Autowired
    private QuestionBankItemRepository questionRepository;

    @Autowired
    private PaperTemplateRepository paperTemplateRepository;

    @Autowired
    private PaperTemplateQuestionRepository paperTemplateQuestionRepository;

    @Autowired
    private PaperReleaseRepository paperReleaseRepository;

    @Autowired
    private PaperReleaseQuestionRepository paperReleaseQuestionRepository;

    @Autowired
    private PaperReleaseTargetRepository paperReleaseTargetRepository;

    @Autowired
    private StudentPaperAttemptRepository studentPaperAttemptRepository;

    @Autowired
    private StudentPaperAnswerRepository studentPaperAnswerRepository;

    @Autowired
    private QuestionImportBatchRepository importBatchRepository;

    @Autowired
    private QuestionImportPreviewRowRepository previewRowRepository;

    @Test
    void enforcesOneAttemptPerReleaseAndStudent() {
        studentPaperAttemptRepository.saveAndFlush(attempt(20L, 7L));

        assertThrows(DataIntegrityViolationException.class,
                () -> studentPaperAttemptRepository.saveAndFlush(attempt(20L, 7L)));
    }

    @Test
    void enforcesOneFrozenTargetPerReleaseAndStudent() {
        paperReleaseTargetRepository.saveAndFlush(target(30L, 8L));

        assertThrows(DataIntegrityViolationException.class,
                () -> paperReleaseTargetRepository.saveAndFlush(target(30L, 8L)));
    }

    @Test
    void enforcesOneAnswerPerAttemptAndReleaseQuestion() {
        studentPaperAnswerRepository.saveAndFlush(answer(41L, 51L));

        assertThrows(DataIntegrityViolationException.class,
                () -> studentPaperAnswerRepository.saveAndFlush(answer(41L, 51L)));
    }

    @Test
    void answerCarriesExplicitReleaseIdentity() {
        Field paperReleaseId = java.util.Arrays.stream(StudentPaperAnswer.class.getDeclaredFields())
                .filter(field -> field.getName().equals("paperReleaseId"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("StudentPaperAnswer must carry paperReleaseId"));

        Column column = paperReleaseId.getAnnotation(Column.class);

        assertEquals("paper_release_id", column.name());
        assertFalse(column.nullable());
    }

    @Test
    void enforcesStableQuestionOrderInsideTemplateAndRelease() {
        paperTemplateQuestionRepository.saveAndFlush(templateQuestion(100L, 1));
        paperReleaseQuestionRepository.saveAndFlush(releaseQuestion(200L, 1));

        assertThrows(DataIntegrityViolationException.class,
                () -> paperTemplateQuestionRepository.saveAndFlush(templateQuestion(100L, 1)));
        assertThrows(DataIntegrityViolationException.class,
                () -> paperReleaseQuestionRepository.saveAndFlush(releaseQuestion(200L, 1)));
    }

    @Test
    void persistsDecimalScoresAndJsonSnapshots() {
        QuestionBankItem question = question();
        QuestionBankItem savedQuestion = questionRepository.saveAndFlush(question);
        PaperTemplate template = paperTemplateRepository.saveAndFlush(template());
        PaperTemplateQuestion templateQuestion = templateQuestion(template.getId(), 1);
        templateQuestion.setSourceQuestionId(savedQuestion.getId());
        PaperTemplateQuestion savedTemplateQuestion = paperTemplateQuestionRepository.saveAndFlush(templateQuestion);
        PaperRelease release = paperReleaseRepository.saveAndFlush(release(template.getId()));
        PaperReleaseQuestion releaseQuestion = releaseQuestion(release.getId(), 1);
        releaseQuestion.setPaperTemplateQuestionId(savedTemplateQuestion.getId());
        releaseQuestion.setSourceQuestionId(savedQuestion.getId());

        PaperReleaseQuestion savedReleaseQuestion = paperReleaseQuestionRepository.saveAndFlush(releaseQuestion);

        assertEquals(new BigDecimal("2.50"), savedQuestion.getDefaultScore());
        assertEquals("[{\"key\":\"A\",\"text\":\"apple\"}]", savedQuestion.getOptionsJson());
        assertEquals("[\"A\"]", savedQuestion.getAcceptedAnswersJson());
        assertEquals(new BigDecimal("5.25"), savedTemplateQuestion.getScore());
        assertEquals(new BigDecimal("5.25"), savedReleaseQuestion.getScore());
        assertEquals("[\"A\"]", savedReleaseQuestion.getAcceptedAnswersJson());
    }

    @Test
    void persistsImportPreviewRowsByBatchAndRowNumber() {
        QuestionImportBatch batch = new QuestionImportBatch();
        batch.setImportedByUserId(3L);
        batch.setFileName("questions.csv");
        batch.setTotalRows(1);
        batch.setValidRows(1);
        QuestionImportBatch savedBatch = importBatchRepository.saveAndFlush(batch);
        previewRowRepository.saveAndFlush(previewRow(savedBatch.getId(), 2));

        assertThrows(DataIntegrityViolationException.class,
                () -> previewRowRepository.saveAndFlush(previewRow(savedBatch.getId(), 2)));
    }

    @Test
    void entitiesDoNotDeclareJpaCascadeRelationships() {
        List<Class<?>> entityTypes = List.of(
                QuestionBankItem.class,
                PaperTemplate.class,
                PaperTemplateQuestion.class,
                PaperRelease.class,
                PaperReleaseQuestion.class,
                PaperReleaseTarget.class,
                StudentPaperAttempt.class,
                StudentPaperAnswer.class,
                QuestionImportBatch.class,
                QuestionImportPreviewRow.class
        );

        for (Class<?> entityType : entityTypes) {
            assertDoesNotThrow(() -> inspectRelationshipAnnotations(entityType));
        }
    }

    private void inspectRelationshipAnnotations(Class<?> entityType) {
        for (Field field : entityType.getDeclaredFields()) {
            assertFalse(field.isAnnotationPresent(OneToMany.class), entityType.getSimpleName());
            assertFalse(field.isAnnotationPresent(ManyToOne.class), entityType.getSimpleName());
            assertFalse(field.isAnnotationPresent(OneToOne.class), entityType.getSimpleName());
            assertFalse(field.isAnnotationPresent(ManyToMany.class), entityType.getSimpleName());
        }
    }

    private QuestionBankItem question() {
        QuestionBankItem question = new QuestionBankItem();
        question.setQuestionType(QuestionType.SINGLE_CHOICE);
        question.setStem("Choose apple");
        question.setOptionsJson("[{\"key\":\"A\",\"text\":\"apple\"}]");
        question.setAcceptedAnswersJson("[\"A\"]");
        question.setDefaultScore(new BigDecimal("2.50"));
        question.setCreatedByUserId(3L);
        question.setStatus(QuestionBankItemStatus.ACTIVE);
        return question;
    }

    private PaperTemplate template() {
        PaperTemplate template = new PaperTemplate();
        template.setTitle("Unit 1 Quiz");
        template.setOwnerUserId(3L);
        template.setTotalScore(new BigDecimal("5.25"));
        return template;
    }

    private PaperTemplateQuestion templateQuestion(Long paperTemplateId, int order) {
        PaperTemplateQuestion question = new PaperTemplateQuestion();
        question.setPaperTemplateId(paperTemplateId);
        question.setQuestionOrder(order);
        question.setQuestionType(QuestionType.SINGLE_CHOICE);
        question.setStem("Choose apple");
        question.setOptionsJson("[{\"key\":\"A\",\"text\":\"apple\"}]");
        question.setAcceptedAnswersJson("[\"A\"]");
        question.setScore(new BigDecimal("5.25"));
        return question;
    }

    private PaperRelease release(Long paperTemplateId) {
        PaperRelease release = new PaperRelease();
        release.setPaperTemplateId(paperTemplateId);
        release.setTitle("Unit 1 Quiz");
        release.setPublishedByUserId(3L);
        release.setStatus(PaperReleaseStatus.SCHEDULED);
        release.setQuestionCount(1);
        release.setTotalScore(new BigDecimal("5.25"));
        release.setBlankAnswerPolicy(PaperBlankAnswerPolicy.ALLOW_BLANK);
        release.setResultVisibility(PaperResultVisibility.SCORE_ONLY);
        return release;
    }

    private PaperReleaseQuestion releaseQuestion(Long paperReleaseId, int order) {
        PaperReleaseQuestion question = new PaperReleaseQuestion();
        question.setPaperReleaseId(paperReleaseId);
        question.setQuestionOrder(order);
        question.setQuestionType(QuestionType.SINGLE_CHOICE);
        question.setStem("Choose apple");
        question.setOptionsJson("[{\"key\":\"A\",\"text\":\"apple\"}]");
        question.setAcceptedAnswersJson("[\"A\"]");
        question.setScore(new BigDecimal("5.25"));
        return question;
    }

    private PaperReleaseTarget target(Long paperReleaseId, Long studentId) {
        PaperReleaseTarget target = new PaperReleaseTarget();
        target.setPaperReleaseId(paperReleaseId);
        target.setStudentId(studentId);
        target.setTargetedByUserId(3L);
        return target;
    }

    private StudentPaperAttempt attempt(Long paperReleaseId, Long studentId) {
        StudentPaperAttempt attempt = new StudentPaperAttempt();
        attempt.setPaperReleaseId(paperReleaseId);
        attempt.setStudentId(studentId);
        attempt.setStatus(StudentPaperAttemptStatus.NOT_STARTED);
        attempt.setTotalScore(new BigDecimal("10.00"));
        return attempt;
    }

    private StudentPaperAnswer answer(Long attemptId, Long releaseQuestionId) {
        StudentPaperAnswer answer = new StudentPaperAnswer();
        answer.setAttemptId(attemptId);
        answer.setPaperReleaseId(61L);
        answer.setReleaseQuestionId(releaseQuestionId);
        answer.setSelectedAnswersJson("[\"A\"]");
        return answer;
    }

    private QuestionImportPreviewRow previewRow(Long batchId, int rowNumber) {
        QuestionImportPreviewRow row = new QuestionImportPreviewRow();
        row.setBatchId(batchId);
        row.setRowNumber(rowNumber);
        row.setStatus(QuestionImportPreviewRowStatus.VALID);
        row.setQuestionType(QuestionType.SINGLE_CHOICE);
        row.setStem("Choose apple");
        row.setOptionsJson("[{\"key\":\"A\",\"text\":\"apple\"}]");
        row.setAcceptedAnswersJson("[\"A\"]");
        row.setScore(new BigDecimal("1.00"));
        return row;
    }
}
