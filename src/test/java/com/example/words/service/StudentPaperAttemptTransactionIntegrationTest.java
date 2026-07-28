package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.words.dto.InvalidatePaperReleaseRequest;
import com.example.words.dto.SaveStudentPaperDraftRequest;
import com.example.words.dto.StudentPaperAnswerRequest;
import com.example.words.dto.SubmitStudentPaperRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ConflictException;
import com.example.words.model.AppUser;
import com.example.words.model.PaperBlankAnswerPolicy;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperReleaseQuestion;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.PaperResultVisibility;
import com.example.words.model.QuestionType;
import com.example.words.model.StudentPaperAnswer;
import com.example.words.model.StudentPaperAttempt;
import com.example.words.model.StudentPaperAttemptStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.PaperReleaseQuestionRepository;
import com.example.words.repository.PaperReleaseRepository;
import com.example.words.repository.StudentPaperAnswerRepository;
import com.example.words.repository.StudentPaperAttemptRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        StudentPaperAttemptService.class,
        PaperReleaseService.class,
        ExamPaperSnapshotService.class,
        ExamPaperAnswerNormalizer.class,
        StudentPaperAttemptTransactionIntegrationTest.AttemptTestConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class StudentPaperAttemptTransactionIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 10, 0);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private StudentPaperAttemptService attemptService;
    @Autowired
    private PaperReleaseService releaseService;
    @Autowired
    private PaperReleaseRepository releaseRepository;
    @Autowired
    private PaperReleaseQuestionRepository questionRepository;
    @Autowired
    private StudentPaperAttemptRepository attemptRepository;
    @Autowired
    private StudentPaperAnswerRepository answerRepository;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PaperTemplateService paperTemplateService;
    @MockBean
    private ExamPaperAccessService accessService;

    private ExecutorService executor;
    private AppUser teacher;
    private AppUser student;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @BeforeEach
    void setUp() {
        teacher = userRepository.saveAndFlush(user("attempt-teacher", UserRole.TEACHER));
        student = userRepository.saveAndFlush(user("attempt-student", UserRole.STUDENT));
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        answerRepository.deleteAll();
        attemptRepository.deleteAll();
        questionRepository.deleteAll();
        releaseRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void staleDraftVersionCannotOverwriteNewerPersistedAnswer() {
        Fixture fixture = fixture(NOW.plusHours(1));
        long initialVersion = fixture.attempt().getVersion();
        attemptService.saveDraft(
                fixture.attempt().getId(),
                draft(initialVersion, fixture.question().getId(), "A"),
                student);

        assertThrows(ConflictException.class, () -> attemptService.saveDraft(
                fixture.attempt().getId(),
                draft(initialVersion, fixture.question().getId(), "B"),
                student));

        StudentPaperAnswer persisted = answerRepository
                .findByAttemptIdAndReleaseQuestionId(
                        fixture.attempt().getId(), fixture.question().getId())
                .orElseThrow();
        assertEquals("[\"A\"]", persisted.getSelectedAnswersJson());
    }

    @Test
    void concurrentDraftSavesWithSameVersionRejectLoserWithoutMixingAnswers() throws Exception {
        Fixture fixture = fixture(NOW.plusHours(1));
        PaperReleaseQuestion secondQuestion = new PaperReleaseQuestion();
        secondQuestion.setPaperReleaseId(fixture.release().getId());
        secondQuestion.setQuestionOrder(2);
        secondQuestion.setQuestionType(QuestionType.SINGLE_CHOICE);
        secondQuestion.setStem("Pick B");
        secondQuestion.setOptionsJson("{\"A\":\"Alpha\",\"B\":\"Beta\"}");
        secondQuestion.setAcceptedAnswersJson("[\"B\"]");
        secondQuestion.setScore(new BigDecimal("5.00"));
        secondQuestion = questionRepository.saveAndFlush(secondQuestion);
        long initialVersion = fixture.attempt().getVersion();
        SaveStudentPaperDraftRequest winnerDraft = new SaveStudentPaperDraftRequest(
                initialVersion,
                List.of(
                        new StudentPaperAnswerRequest(fixture.question().getId(), List.of("A"), List.of()),
                        new StudentPaperAnswerRequest(secondQuestion.getId(), List.of("B"), List.of())));
        SaveStudentPaperDraftRequest staleDraft = new SaveStudentPaperDraftRequest(
                initialVersion,
                List.of(
                        new StudentPaperAnswerRequest(fixture.question().getId(), List.of("B"), List.of()),
                        new StudentPaperAnswerRequest(secondQuestion.getId(), List.of("A"), List.of())));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch winnerHasLocks = new CountDownLatch(1);
        CountDownLatch allowWinnerCommit = new CountDownLatch(1);
        CountDownLatch staleRequestStarted = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);
        Future<?> winner = executor.submit(() -> transaction.executeWithoutResult(status -> {
            attemptService.saveDraft(fixture.attempt().getId(), winnerDraft, student);
            winnerHasLocks.countDown();
            awaitSignal(allowWinnerCommit, "winning draft commit release");
        }));
        awaitSignal(winnerHasLocks, "winning draft lock acquisition");
        Future<?> stale = executor.submit(() -> {
            staleRequestStarted.countDown();
            return attemptService.saveDraft(fixture.attempt().getId(), staleDraft, student);
        });
        awaitSignal(staleRequestStarted, "stale draft request start");
        try {
            assertNotNull(pollForBlockedReleaseLock(),
                    "PostgreSQL must report the stale draft waiting on the release lock");
        } finally {
            allowWinnerCommit.countDown();
        }
        winner.get(10, TimeUnit.SECONDS);
        ExecutionException staleFailure = assertThrows(
                ExecutionException.class, () -> stale.get(10, TimeUnit.SECONDS));
        assertInstanceOf(ConflictException.class, staleFailure.getCause());

        StudentPaperAttempt persistedAttempt = attemptRepository.findById(fixture.attempt().getId()).orElseThrow();
        StudentPaperAnswer firstAnswer = answerRepository.findByAttemptIdAndReleaseQuestionId(
                fixture.attempt().getId(), fixture.question().getId()).orElseThrow();
        StudentPaperAnswer secondAnswer = answerRepository.findByAttemptIdAndReleaseQuestionId(
                fixture.attempt().getId(), secondQuestion.getId()).orElseThrow();
        assertEquals(initialVersion + 1, persistedAttempt.getVersion());
        assertEquals(2, persistedAttempt.getAnsweredCount());
        assertEquals("[\"A\"]", firstAnswer.getSelectedAnswersJson());
        assertEquals("[\"B\"]", secondAnswer.getSelectedAnswersJson());
        assertEquals(2, answerRepository.count());
    }

    @Test
    void listingAssignedPaperPersistsOverdueState() {
        Fixture fixture = fixture(NOW.minusMinutes(1));

        assertEquals(
                StudentPaperAttemptStatus.OVERDUE,
                attemptService.listAssigned(student).get(0).getAttemptStatus());

        assertEquals(
                StudentPaperAttemptStatus.OVERDUE,
                attemptRepository.findById(fixture.attempt().getId()).orElseThrow().getStatus());
    }

    @Test
    void listingStartedScheduledPaperPersistsOpenReleaseState() {
        Fixture fixture = fixture(NOW.plusMinutes(1));
        fixture.release().setStatus(PaperReleaseStatus.SCHEDULED);
        releaseRepository.saveAndFlush(fixture.release());

        assertEquals(
                PaperReleaseStatus.OPEN,
                attemptService.listAssigned(student).get(0).getReleaseStatus());

        assertEquals(
                PaperReleaseStatus.OPEN,
                releaseRepository.findById(fixture.release().getId()).orElseThrow().getStatus());
    }

    @Test
    void correctionWaitsForOnTimeSubmitAndPreservesSubmittedStatus() throws Exception {
        assertSubmitWinsCorrection(NOW.plusMinutes(1), StudentPaperAttemptStatus.SUBMITTED);
    }

    @Test
    void correctionWaitsForLateSubmitAndPreservesSubmittedLateStatus() throws Exception {
        assertSubmitWinsCorrection(NOW.minusMinutes(1), StudentPaperAttemptStatus.SUBMITTED_LATE);
    }

    @Test
    void submitWaitsForCorrectionAndCannotCreateSplitTerminalState() throws Exception {
        Fixture fixture = fixture(NOW.plusMinutes(1));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch correctionHasLocks = new CountDownLatch(1);
        CountDownLatch allowCorrectionCommit = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);
        Future<?> correction = executor.submit(() -> transaction.executeWithoutResult(status -> {
            releaseService.invalidate(
                    fixture.release().getId(),
                    new InvalidatePaperReleaseRequest("Wrong key"),
                    teacher);
            correctionHasLocks.countDown();
            awaitSignal(allowCorrectionCommit, "correction commit release");
        }));
        awaitSignal(correctionHasLocks, "correction lock acquisition");
        Future<?> submit = executor.submit(() -> attemptService.submit(
                fixture.attempt().getId(),
                submit(fixture.attempt().getVersion(), fixture.question().getId(), "A"),
                student));
        try {
            assertNotNull(pollForDatabaseBlocker(),
                    "PostgreSQL must report final submit waiting on the release correction lock");
        } finally {
            allowCorrectionCommit.countDown();
        }
        correction.get(10, TimeUnit.SECONDS);
        ExecutionException failure = assertThrows(
                ExecutionException.class, () -> submit.get(10, TimeUnit.SECONDS));
        assertInstanceOf(BadRequestException.class, failure.getCause());

        PaperRelease persistedRelease = releaseRepository.findById(fixture.release().getId()).orElseThrow();
        StudentPaperAttempt persistedAttempt = attemptRepository.findById(fixture.attempt().getId()).orElseThrow();
        assertEquals(PaperReleaseStatus.INVALIDATED, persistedRelease.getStatus());
        assertEquals(StudentPaperAttemptStatus.INVALIDATED, persistedAttempt.getStatus());
        assertEquals(0, answerRepository.count());
    }

    private void assertSubmitWinsCorrection(
            LocalDateTime deadline, StudentPaperAttemptStatus expectedStatus) throws Exception {
        Fixture fixture = fixture(deadline);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch submitHasLocks = new CountDownLatch(1);
        CountDownLatch allowSubmitCommit = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);
        Future<?> submit = executor.submit(() -> transaction.executeWithoutResult(status -> {
            attemptService.submit(
                    fixture.attempt().getId(),
                    submit(fixture.attempt().getVersion(), fixture.question().getId(), "A"),
                    student);
            submitHasLocks.countDown();
            awaitSignal(allowSubmitCommit, "submit commit release");
        }));
        awaitSignal(submitHasLocks, "submit lock acquisition");
        Future<?> correction = executor.submit(() -> releaseService.invalidate(
                fixture.release().getId(),
                new InvalidatePaperReleaseRequest("Wrong key"),
                teacher));
        try {
            assertNotNull(pollForDatabaseBlocker(),
                    "PostgreSQL must report release correction waiting on the submit lock");
        } finally {
            allowSubmitCommit.countDown();
        }
        submit.get(10, TimeUnit.SECONDS);
        correction.get(10, TimeUnit.SECONDS);

        PaperRelease persistedRelease = releaseRepository.findById(fixture.release().getId()).orElseThrow();
        StudentPaperAttempt persistedAttempt = attemptRepository.findById(fixture.attempt().getId()).orElseThrow();
        assertEquals(PaperReleaseStatus.INVALIDATED, persistedRelease.getStatus());
        assertEquals(expectedStatus, persistedAttempt.getStatus());
        assertEquals(new BigDecimal("5.00"), persistedAttempt.getEarnedScore());
        assertEquals("Wrong key", persistedAttempt.getInvalidateReason());
        assertEquals(1, answerRepository.count());
    }

    private Integer pollForDatabaseBlocker() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            Integer blockedSessions = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM pg_stat_activity
                    WHERE wait_event_type = 'Lock'
                      AND cardinality(pg_blocking_pids(pid)) > 0
                    """, Integer.class);
            if (blockedSessions != null && blockedSessions > 0) {
                return blockedSessions;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        } while (System.nanoTime() < deadline);
        return null;
    }

    private Integer pollForBlockedReleaseLock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            Integer blockedSessions = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM pg_stat_activity
                    WHERE wait_event_type = 'Lock'
                      AND cardinality(pg_blocking_pids(pid)) > 0
                      AND query ILIKE '%paper_releases%'
                    """, Integer.class);
            if (blockedSessions != null && blockedSessions > 0) {
                return blockedSessions;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        } while (System.nanoTime() < deadline);
        return null;
    }

    private Fixture fixture(LocalDateTime deadline) {
        PaperRelease release = new PaperRelease();
        release.setPaperTemplateId(10L);
        release.setTitle("Transactional paper");
        release.setPublishedByUserId(teacher.getId());
        release.setStatus(PaperReleaseStatus.OPEN);
        release.setQuestionCount(1);
        release.setTotalScore(new BigDecimal("5.00"));
        release.setShuffleQuestions(false);
        release.setShuffleOptions(false);
        release.setStartTime(NOW.minusHours(1));
        release.setDeadline(deadline);
        release.setBlankAnswerPolicy(PaperBlankAnswerPolicy.ALLOW_BLANK);
        release.setResultVisibility(PaperResultVisibility.SCORE_ONLY);
        release.setShowSupersededToStudents(false);
        release = releaseRepository.saveAndFlush(release);

        PaperReleaseQuestion question = new PaperReleaseQuestion();
        question.setPaperReleaseId(release.getId());
        question.setQuestionOrder(1);
        question.setQuestionType(QuestionType.SINGLE_CHOICE);
        question.setStem("Pick A");
        question.setOptionsJson("{\"A\":\"Alpha\",\"B\":\"Beta\"}");
        question.setAcceptedAnswersJson("[\"A\"]");
        question.setScore(new BigDecimal("5.00"));
        question = questionRepository.saveAndFlush(question);

        StudentPaperAttempt attempt = new StudentPaperAttempt();
        attempt.setPaperReleaseId(release.getId());
        attempt.setStudentId(student.getId());
        attempt.setStatus(StudentPaperAttemptStatus.IN_PROGRESS);
        attempt.setAnsweredCount(0);
        attempt.setCorrectCount(0);
        attempt.setEarnedScore(BigDecimal.ZERO);
        attempt.setTotalScore(new BigDecimal("5.00"));
        attempt = attemptRepository.saveAndFlush(attempt);
        return new Fixture(release, question, attempt);
    }

    private SaveStudentPaperDraftRequest draft(long version, Long releaseQuestionId, String answer) {
        return new SaveStudentPaperDraftRequest(
                version,
                List.of(new StudentPaperAnswerRequest(releaseQuestionId, List.of(answer), List.of())));
    }

    private SubmitStudentPaperRequest submit(
            long version, Long releaseQuestionId, String answer) {
        return new SubmitStudentPaperRequest(
                version,
                List.of(new StudentPaperAnswerRequest(
                        releaseQuestionId, List.of(answer), List.of())));
    }

    private void awaitSignal(CountDownLatch signal, String description) {
        try {
            if (!signal.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for " + description);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for " + description, exception);
        }
    }

    private AppUser user(String username, UserRole role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash("hash");
        user.setDisplayName(username);
        user.setRole(role);
        return user;
    }

    private record Fixture(
            PaperRelease release, PaperReleaseQuestion question, StudentPaperAttempt attempt) {
    }

    @TestConfiguration
    static class AttemptTestConfiguration {

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
