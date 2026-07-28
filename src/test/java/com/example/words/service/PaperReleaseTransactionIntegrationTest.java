package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.words.dto.InvalidatePaperReleaseRequest;
import com.example.words.dto.PublishPaperRequest;
import com.example.words.model.AppUser;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperTemplate;
import com.example.words.model.PaperTemplateQuestion;
import com.example.words.model.PaperTemplateStatus;
import com.example.words.model.QuestionType;
import com.example.words.model.StudentPaperAttempt;
import com.example.words.model.StudentPaperAttemptStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.PaperReleaseQuestionRepository;
import com.example.words.repository.PaperReleaseRepository;
import com.example.words.repository.PaperReleaseTargetRepository;
import com.example.words.repository.StudentPaperAttemptRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import({
        PaperReleaseService.class,
        ExamPaperSnapshotService.class,
        PaperReleaseTransactionIntegrationTest.ReleaseTestConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaperReleaseTransactionIntegrationTest {

    @Autowired
    private PaperReleaseService service;
    @Autowired
    private PaperReleaseRepository releaseRepository;
    @Autowired
    private PaperReleaseQuestionRepository questionRepository;
    @Autowired
    private PaperReleaseTargetRepository targetRepository;
    @Autowired
    private StudentPaperAttemptRepository attemptRepository;
    @Autowired
    private AppUserRepository userRepository;

    @MockBean
    private PaperTemplateService paperTemplateService;
    @MockBean
    private ExamPaperAccessService accessService;

    private AppUser teacher;
    private AppUser student;

    @BeforeEach
    void setUp() {
        teacher = userRepository.saveAndFlush(user("release-teacher", UserRole.TEACHER));
        student = userRepository.saveAndFlush(user("release-student", UserRole.STUDENT));
    }

    @AfterEach
    void tearDown() {
        attemptRepository.deleteAll();
        targetRepository.deleteAll();
        questionRepository.deleteAll();
        releaseRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void snapshotConstraintFailureRollsBackEntirePublishAggregate() {
        when(paperTemplateService.lockReadyForPublishing(10L, teacher))
                .thenReturn(source(question(1, "Valid", "[\"answer\"]"), question(2, "Invalid", null)));

        assertThrows(DataIntegrityViolationException.class, () -> service.publish(
                request(), teacher));

        assertEquals(0, releaseRepository.count());
        assertEquals(0, questionRepository.count());
        assertEquals(0, targetRepository.count());
        assertEquals(0, attemptRepository.count());
    }

    @Test
    void invalidationPersistsAuditStateAndPreservesSubmittedScore() {
        when(paperTemplateService.lockReadyForPublishing(10L, teacher))
                .thenReturn(source(question(1, "Valid", "[\"answer\"]")));
        Long releaseId = service.publish(request(), teacher).getId();
        StudentPaperAttempt attempt = attemptRepository
                .findByPaperReleaseIdAndStudentId(releaseId, student.getId()).orElseThrow();
        attempt.setStatus(StudentPaperAttemptStatus.SUBMITTED);
        attempt.setEarnedScore(new BigDecimal("1.00"));
        attempt.setCorrectCount(1);
        attempt.setAnsweredCount(1);
        attemptRepository.saveAndFlush(attempt);

        service.invalidate(releaseId, new InvalidatePaperReleaseRequest("Wrong answer key"), teacher);

        PaperRelease release = releaseRepository.findById(releaseId).orElseThrow();
        StudentPaperAttempt invalidated = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertEquals("Wrong answer key", release.getInvalidateReason());
        assertEquals(StudentPaperAttemptStatus.SUBMITTED, invalidated.getStatus());
        assertEquals(new BigDecimal("1.00"), invalidated.getEarnedScore());
        assertEquals("Wrong answer key", invalidated.getInvalidateReason());
    }

    private PublishPaperRequest request() {
        return new PublishPaperRequest(10L, List.of(student.getId()), List.of(), null, null, null, null);
    }

    private PaperTemplateService.PublicationSource source(PaperTemplateQuestion... questions) {
        PaperTemplate paper = new PaperTemplate();
        paper.setId(10L);
        paper.setTitle("Transactional release");
        paper.setOwnerUserId(teacher.getId());
        paper.setStatus(PaperTemplateStatus.READY);
        paper.setShuffleQuestions(false);
        paper.setShuffleOptions(false);
        paper.setTotalScore(BigDecimal.valueOf(questions.length));
        return new PaperTemplateService.PublicationSource(paper, List.of(questions));
    }

    private PaperTemplateQuestion question(int order, String stem, String acceptedAnswers) {
        PaperTemplateQuestion question = new PaperTemplateQuestion();
        question.setId((long) order);
        question.setPaperTemplateId(10L);
        question.setQuestionOrder(order);
        question.setQuestionType(QuestionType.FILL_IN_BLANK);
        question.setStem(stem);
        question.setAcceptedAnswersJson(acceptedAnswers);
        question.setScore(BigDecimal.ONE);
        return question;
    }

    private AppUser user(String username, UserRole role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash("hash");
        user.setDisplayName(username);
        user.setRole(role);
        return user;
    }

    @TestConfiguration
    static class ReleaseTestConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-07-29T02:00:00Z"), ZoneOffset.ofHours(8));
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
