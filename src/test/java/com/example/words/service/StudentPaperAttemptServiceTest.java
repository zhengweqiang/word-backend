package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.example.words.dto.StudentAssignedPaperSummaryResponse;
import com.example.words.dto.SaveStudentPaperDraftRequest;
import com.example.words.dto.StudentPaperAnswerRequest;
import com.example.words.dto.StudentPaperAttemptResponse;
import com.example.words.dto.StudentPaperResultResponse;
import com.example.words.dto.SubmitStudentPaperRequest;
import com.example.words.dto.SubmitStudentPaperResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ConflictException;
import com.example.words.model.AppUser;
import com.example.words.model.PaperBlankAnswerPolicy;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperReleaseQuestion;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.PaperResultVisibility;
import com.example.words.model.QuestionType;
import com.example.words.model.StudentPaperAttempt;
import com.example.words.model.StudentPaperAttemptStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.PaperReleaseQuestionRepository;
import com.example.words.repository.PaperReleaseRepository;
import com.example.words.repository.StudentPaperAnswerRepository;
import com.example.words.repository.StudentPaperAttemptRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentPaperAttemptServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 10, 0);

    @Mock
    private PaperReleaseRepository releaseRepository;
    @Mock
    private PaperReleaseQuestionRepository releaseQuestionRepository;
    @Mock
    private StudentPaperAttemptRepository attemptRepository;
    @Mock
    private StudentPaperAnswerRepository answerRepository;
    @Mock
    private StudentPointEventPublisher studentPointEventPublisher;

    private StudentPaperAttemptService service;
    private AppUser student;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-29T02:00:00Z"), ZoneOffset.ofHours(8));
        service = new StudentPaperAttemptService(
                releaseRepository,
                releaseQuestionRepository,
                attemptRepository,
                answerRepository,
                new ExamPaperAnswerNormalizer(),
                new ObjectMapper(),
                studentPointEventPublisher,
                clock);
        student = user(20L, UserRole.STUDENT);
    }

    @Test
    void listsAssignedAttemptsAndExposesScheduledShellMetadata() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.NOT_STARTED);
        PaperRelease release = release(10L, PaperReleaseStatus.SCHEDULED, NOW.plusHours(1), NOW.plusHours(2));
        when(attemptRepository.findByStudentIdOrderByCreatedAtDesc(student.getId())).thenReturn(List.of(attempt));
        when(releaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(release));
        when(attemptRepository.findByIdAndStudentIdForUpdate(100L, student.getId()))
                .thenReturn(Optional.of(attempt));

        List<StudentAssignedPaperSummaryResponse> result = service.listAssigned(student);

        verify(releaseRepository).findByIdForUpdate(10L);
        verify(attemptRepository).findByIdAndStudentIdForUpdate(100L, student.getId());
        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getAttemptId());
        assertEquals("Vocabulary check", result.get(0).getTitle());
        assertEquals(StudentPaperAttemptStatus.NOT_STARTED, result.get(0).getAttemptStatus());
        assertFalse(result.get(0).getAnswerable());
    }

    @Test
    void listPersistsOverdueAfterLockingReleaseThenStudentAttempt() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(2), NOW.minusMinutes(1));
        when(attemptRepository.findByStudentIdOrderByCreatedAtDesc(student.getId())).thenReturn(List.of(attempt));
        when(releaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(release));
        when(attemptRepository.findByIdAndStudentIdForUpdate(100L, student.getId()))
                .thenReturn(Optional.of(attempt));

        List<StudentAssignedPaperSummaryResponse> result = service.listAssigned(student);

        assertEquals(StudentPaperAttemptStatus.OVERDUE, result.get(0).getAttemptStatus());
        assertEquals(StudentPaperAttemptStatus.OVERDUE, attempt.getStatus());
        verify(attemptRepository).saveAndFlush(attempt);
        InOrder locks = inOrder(releaseRepository, attemptRepository);
        locks.verify(releaseRepository).findByIdForUpdate(10L);
        locks.verify(attemptRepository).findByIdAndStudentIdForUpdate(100L, student.getId());
    }

    @Test
    void listDoesNotRewriteAttemptThatIsAlreadyOverdue() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.OVERDUE);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(2), NOW.minusMinutes(1));
        when(attemptRepository.findByStudentIdOrderByCreatedAtDesc(student.getId())).thenReturn(List.of(attempt));
        when(releaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(release));
        when(attemptRepository.findByIdAndStudentIdForUpdate(100L, student.getId()))
                .thenReturn(Optional.of(attempt));

        List<StudentAssignedPaperSummaryResponse> result = service.listAssigned(student);

        assertEquals(StudentPaperAttemptStatus.OVERDUE, result.get(0).getAttemptStatus());
        verify(attemptRepository, never()).saveAndFlush(attempt);
    }

    @Test
    void listActivatesStartedScheduledReleaseUnderLock() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.NOT_STARTED);
        PaperRelease release = release(10L, PaperReleaseStatus.SCHEDULED, NOW.minusMinutes(1), NOW.plusHours(1));
        when(attemptRepository.findByStudentIdOrderByCreatedAtDesc(student.getId())).thenReturn(List.of(attempt));
        when(releaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(release));
        when(attemptRepository.findByIdAndStudentIdForUpdate(100L, student.getId()))
                .thenReturn(Optional.of(attempt));

        List<StudentAssignedPaperSummaryResponse> result = service.listAssigned(student);

        assertEquals(PaperReleaseStatus.OPEN, result.get(0).getReleaseStatus());
        assertTrue(result.get(0).getAnswerable());
        assertEquals(PaperReleaseStatus.OPEN, release.getStatus());
        verify(releaseRepository).saveAndFlush(release);
    }

    @Test
    void listExcludesWithdrawnInvalidatedAndHiddenSupersededReleases() {
        StudentPaperAttempt withdrawnAttempt = attempt(100L, 10L, StudentPaperAttemptStatus.NOT_STARTED);
        StudentPaperAttempt invalidatedAttempt = attempt(101L, 11L, StudentPaperAttemptStatus.INVALIDATED);
        StudentPaperAttempt hiddenAttempt = attempt(102L, 12L, StudentPaperAttemptStatus.SUBMITTED);
        StudentPaperAttempt visibleAttempt = attempt(103L, 13L, StudentPaperAttemptStatus.SUBMITTED);
        PaperRelease withdrawn = release(10L, PaperReleaseStatus.WITHDRAWN, NOW.minusHours(1), null);
        PaperRelease invalidated = release(11L, PaperReleaseStatus.INVALIDATED, NOW.minusHours(1), null);
        PaperRelease hidden = release(12L, PaperReleaseStatus.SUPERSEDED, NOW.minusHours(1), null);
        PaperRelease visible = release(13L, PaperReleaseStatus.SUPERSEDED, NOW.minusHours(1), null);
        hidden.setShowSupersededToStudents(false);
        visible.setShowSupersededToStudents(true);
        when(attemptRepository.findByStudentIdOrderByCreatedAtDesc(student.getId()))
                .thenReturn(List.of(withdrawnAttempt, invalidatedAttempt, hiddenAttempt, visibleAttempt));
        when(releaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(withdrawn));
        when(releaseRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(invalidated));
        when(releaseRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(hidden));
        when(releaseRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(visible));
        when(attemptRepository.findByIdAndStudentIdForUpdate(103L, student.getId()))
                .thenReturn(Optional.of(visibleAttempt));

        List<StudentAssignedPaperSummaryResponse> result = service.listAssigned(student);

        assertEquals(List.of(103L), result.stream()
                .map(StudentAssignedPaperSummaryResponse::getAttemptId).toList());
        verify(attemptRepository, never()).findByIdAndStudentIdForUpdate(100L, student.getId());
        verify(attemptRepository, never()).findByIdAndStudentIdForUpdate(101L, student.getId());
        verify(attemptRepository, never()).findByIdAndStudentIdForUpdate(102L, student.getId());
    }

    @Test
    void scheduledOpenReturnsShellWithoutQuestionsOrAnswers() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.NOT_STARTED);
        PaperRelease release = release(10L, PaperReleaseStatus.SCHEDULED, NOW.plusHours(1), NOW.plusHours(2));
        stubLockedAttempt(attempt, release);

        StudentPaperAttemptResponse result = service.open(100L, student);

        assertFalse(result.getAnswerable());
        assertTrue(result.getQuestions().isEmpty());
        assertTrue(result.getAnswers().isEmpty());
        assertEquals(StudentPaperAttemptStatus.NOT_STARTED, attempt.getStatus());
        verify(releaseQuestionRepository, never()).findByPaperReleaseIdOrderByQuestionOrderAsc(10L);
        verify(answerRepository, never()).findByAttemptId(100L);
    }

    @Test
    void openingAvailableAttemptLocksReleaseThenAttemptAndMovesToInProgress() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.NOT_STARTED);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        PaperReleaseQuestion question = question(1000L, 10L, QuestionType.SINGLE_CHOICE);
        stubLockedAttempt(attempt, release);
        when(releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(10L))
                .thenReturn(List.of(question));
        when(answerRepository.findByAttemptId(100L)).thenReturn(List.of());
        when(attemptRepository.saveAndFlush(attempt)).thenReturn(attempt);

        StudentPaperAttemptResponse result = service.open(100L, student);

        assertTrue(result.getAnswerable());
        assertEquals(StudentPaperAttemptStatus.IN_PROGRESS, attempt.getStatus());
        assertEquals(NOW, attempt.getOpenedAt());
        assertEquals(1, result.getQuestions().size());
        assertTrue(result.getQuestions().get(0).getOptions().containsKey("A"));
        InOrder locks = inOrder(releaseRepository, attemptRepository);
        locks.verify(releaseRepository).findByIdForUpdate(10L);
        locks.verify(attemptRepository).findByIdAndStudentIdForUpdate(100L, student.getId());
    }

    @Test
    void rejectsNonStudentAndAnotherStudentsAttempt() {
        assertThrows(AccessDeniedException.class, () -> service.listAssigned(user(7L, UserRole.TEACHER)));
        when(attemptRepository.findPaperReleaseIdByIdAndStudentId(100L, student.getId()))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.open(100L, student));
    }

    @Test
    void savesNormalizedDraftAnswersAsSeparateRowsAndResumesThem() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        PaperReleaseQuestion choice = question(1000L, 10L, QuestionType.MULTIPLE_CHOICE);
        PaperReleaseQuestion blank = question(1001L, 10L, QuestionType.FILL_IN_BLANK);
        stubLockedAttempt(attempt, release);
        when(releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(10L))
                .thenReturn(List.of(choice, blank));
        when(answerRepository.findByAttemptId(100L)).thenReturn(List.of());
        SaveStudentPaperDraftRequest request = new SaveStudentPaperDraftRequest(
                3L,
                List.of(
                        new StudentPaperAnswerRequest(1000L, List.of(" b ", "A"), List.of()),
                        new StudentPaperAnswerRequest(1001L, List.of(), List.of("  Word  "))));

        StudentPaperAttemptResponse saved = service.saveDraft(100L, request, student);

        assertEquals(StudentPaperAttemptStatus.IN_PROGRESS, saved.getAttemptStatus());
        assertEquals(2, attempt.getAnsweredCount());
        assertEquals(NOW, attempt.getLastDraftSavedAt());
        verify(answerRepository).saveAllAndFlush(org.mockito.ArgumentMatchers.argThat(answers -> {
            List<com.example.words.model.StudentPaperAnswer> rows =
                    (List<com.example.words.model.StudentPaperAnswer>) answers;
            return rows.size() == 2
                    && "[\"A\",\"B\"]".equals(rows.get(0).getSelectedAnswersJson())
                    && "[\"Word\"]".equals(rows.get(1).getBlankAnswersJson())
                    && rows.stream().allMatch(answer -> answer.getPaperReleaseId().equals(10L));
        }));
    }

    @ParameterizedTest(name = "draft rejects {0}")
    @MethodSource("malformedAnswerShapes")
    void draftRejectsMalformedAnswerShapeAgainstFrozenQuestion(
            String description, QuestionType type, List<String> selected, List<String> blanks) {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        PaperReleaseQuestion question = scoredQuestion(
                1000L, 10L, type, acceptedAnswers(type), "5.00");
        stubSubmit(attempt, release, List.of(question), List.of());

        assertThrows(BadRequestException.class, () -> service.saveDraft(
                100L,
                new SaveStudentPaperDraftRequest(
                        3L,
                        List.of(new StudentPaperAnswerRequest(1000L, selected, blanks))),
                student));

        verify(answerRepository, never()).saveAllAndFlush(anyList());
        verify(attemptRepository, never()).saveAndFlush(attempt);
    }

    @ParameterizedTest(name = "submit rejects {0}")
    @MethodSource("malformedAnswerShapes")
    void submitRejectsMalformedAnswerShapeBeforeFinalization(
            String description, QuestionType type, List<String> selected, List<String> blanks) {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        PaperReleaseQuestion question = scoredQuestion(
                1000L, 10L, type, acceptedAnswers(type), "5.00");
        stubSubmit(attempt, release, List.of(question), List.of());

        assertThrows(BadRequestException.class, () -> service.submit(
                100L,
                new SubmitStudentPaperRequest(
                        3L,
                        List.of(new StudentPaperAnswerRequest(1000L, selected, blanks))),
                student));

        assertEquals(StudentPaperAttemptStatus.IN_PROGRESS, attempt.getStatus());
        verify(answerRepository, never()).saveAllAndFlush(anyList());
        verify(attemptRepository, never()).saveAndFlush(attempt);
    }

    @Test
    void submitValidatesEveryStoredAnswerBeforeFinalizingAnyQuestion() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        PaperReleaseQuestion firstQuestion = scoredQuestion(
                1000L, 10L, QuestionType.SINGLE_CHOICE, "[\"A\"]", "2.00");
        PaperReleaseQuestion secondQuestion = scoredQuestion(
                1001L, 10L, QuestionType.FILL_IN_BLANK, "[\"word\"]", "3.00");
        com.example.words.model.StudentPaperAnswer firstAnswer = new com.example.words.model.StudentPaperAnswer();
        firstAnswer.setAttemptId(100L);
        firstAnswer.setPaperReleaseId(10L);
        firstAnswer.setReleaseQuestionId(1000L);
        firstAnswer.setSelectedAnswersJson("[\"A\"]");
        firstAnswer.setBlankAnswersJson("[]");
        com.example.words.model.StudentPaperAnswer malformedSecondAnswer =
                new com.example.words.model.StudentPaperAnswer();
        malformedSecondAnswer.setAttemptId(100L);
        malformedSecondAnswer.setPaperReleaseId(10L);
        malformedSecondAnswer.setReleaseQuestionId(1001L);
        malformedSecondAnswer.setSelectedAnswersJson("[]");
        malformedSecondAnswer.setBlankAnswersJson("[\"wrong\",\"word\"]");
        stubSubmit(
                attempt,
                release,
                List.of(firstQuestion, secondQuestion),
                List.of(firstAnswer, malformedSecondAnswer));

        assertThrows(BadRequestException.class, () -> service.submit(
                100L,
                new SubmitStudentPaperRequest(3L, List.of()),
                student));

        assertNull(firstAnswer.getFinalizedAt());
        assertNull(firstAnswer.getCorrect());
        verify(answerRepository, never()).saveAllAndFlush(anyList());
        verify(attemptRepository, never()).saveAndFlush(attempt);
    }

    @Test
    void draftAllowsOneValidOrEmptyAnswerForEachQuestionType() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        List<PaperReleaseQuestion> questions = List.of(
                scoredQuestion(1000L, 10L, QuestionType.SINGLE_CHOICE, "[\"A\"]", "1.00"),
                scoredQuestion(1001L, 10L, QuestionType.MULTIPLE_CHOICE, "[\"A\",\"C\"]", "2.00"),
                scoredQuestion(1002L, 10L, QuestionType.FILL_IN_BLANK, "[\"word\"]", "2.00"));
        stubSubmit(attempt, release, questions, List.of());

        service.saveDraft(
                100L,
                new SaveStudentPaperDraftRequest(
                        3L,
                        List.of(
                                new StudentPaperAnswerRequest(1000L, List.of("a"), List.of()),
                                new StudentPaperAnswerRequest(1001L, List.of(), List.of()),
                                new StudentPaperAnswerRequest(1002L, List.of(), List.of("  Word  ")))),
                student);

        verify(answerRepository).saveAllAndFlush(argThat(answers ->
                ((List<?>) answers).size() == 3));
    }

    @Test
    void rejectsStaleDraftVersionWithoutWritingAnswers() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        stubLockedAttempt(attempt, release);
        SaveStudentPaperDraftRequest request = new SaveStudentPaperDraftRequest(2L, List.of());

        assertThrows(ConflictException.class, () -> service.saveDraft(100L, request, student));

        verify(answerRepository, never()).saveAllAndFlush(org.mockito.ArgumentMatchers.anyList());
        verify(attemptRepository, never()).saveAndFlush(attempt);
    }

    @Test
    void rejectsDraftBeforeStartAndAfterFinalSubmission() {
        StudentPaperAttempt scheduledAttempt = attempt(100L, 10L, StudentPaperAttemptStatus.NOT_STARTED);
        PaperRelease scheduled = release(10L, PaperReleaseStatus.SCHEDULED, NOW.plusMinutes(1), NOW.plusHours(1));
        stubLockedAttempt(scheduledAttempt, scheduled);

        assertThrows(BadRequestException.class, () -> service.saveDraft(
                100L, new SaveStudentPaperDraftRequest(3L, List.of()), student));

        StudentPaperAttempt submittedAttempt = attempt(101L, 11L, StudentPaperAttemptStatus.SUBMITTED);
        PaperRelease open = release(11L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        when(attemptRepository.findPaperReleaseIdByIdAndStudentId(101L, student.getId()))
                .thenReturn(Optional.of(11L));
        when(releaseRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(open));
        when(attemptRepository.findByIdAndStudentIdForUpdate(101L, student.getId()))
                .thenReturn(Optional.of(submittedAttempt));

        assertThrows(ConflictException.class, () -> service.saveDraft(
                101L, new SaveStudentPaperDraftRequest(3L, List.of()), student));
    }

    @Test
    void withdrawnOrInvalidatedReleaseCannotBeOpenedEvenBeforeStart() {
        StudentPaperAttempt withdrawnAttempt = attempt(100L, 10L, StudentPaperAttemptStatus.NOT_STARTED);
        PaperRelease withdrawn = release(
                10L, PaperReleaseStatus.WITHDRAWN, NOW.plusHours(1), NOW.plusHours(2));
        stubLockedAttempt(withdrawnAttempt, withdrawn);
        assertThrows(BadRequestException.class, () -> service.open(100L, student));

        StudentPaperAttempt invalidatedAttempt = attempt(101L, 11L, StudentPaperAttemptStatus.INVALIDATED);
        PaperRelease invalidated = release(
                11L, PaperReleaseStatus.INVALIDATED, NOW.plusHours(1), NOW.plusHours(2));
        when(attemptRepository.findPaperReleaseIdByIdAndStudentId(101L, student.getId()))
                .thenReturn(Optional.of(11L));
        when(releaseRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(invalidated));
        when(attemptRepository.findByIdAndStudentIdForUpdate(101L, student.getId()))
                .thenReturn(Optional.of(invalidatedAttempt));
        assertThrows(BadRequestException.class, () -> service.open(101L, student));
    }

    @Test
    void visibleSupersededReleaseCanBeReadButNeverStartsANewAttempt() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.NOT_STARTED);
        PaperRelease release = release(10L, PaperReleaseStatus.SUPERSEDED, NOW.minusHours(1), NOW.plusHours(1));
        release.setShowSupersededToStudents(true);
        stubLockedAttempt(attempt, release);
        when(releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(10L))
                .thenReturn(List.of(question(1000L, 10L, QuestionType.SINGLE_CHOICE)));
        when(answerRepository.findByAttemptId(100L)).thenReturn(List.of());

        StudentPaperAttemptResponse response = service.open(100L, student);

        assertFalse(response.getAnswerable());
        assertEquals(StudentPaperAttemptStatus.NOT_STARTED, response.getAttemptStatus());
        verify(attemptRepository, never()).saveAndFlush(attempt);
    }

    @Test
    void openingVisibleSupersededReleaseAfterDeadlinePersistsOverdueState() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(
                10L, PaperReleaseStatus.SUPERSEDED, NOW.minusHours(2), NOW.minusMinutes(1));
        release.setShowSupersededToStudents(true);
        stubLockedAttempt(attempt, release);
        when(releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(10L))
                .thenReturn(List.of(question(1000L, 10L, QuestionType.SINGLE_CHOICE)));
        when(answerRepository.findByAttemptId(100L)).thenReturn(List.of());

        StudentPaperAttemptResponse response = service.open(100L, student);

        assertEquals(StudentPaperAttemptStatus.OVERDUE, response.getAttemptStatus());
        assertFalse(response.getAnswerable());
        verify(attemptRepository).saveAndFlush(attempt);
    }

    @Test
    void openResumesPreviouslySavedAnswersWithoutGradingData() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        PaperReleaseQuestion question = question(1000L, 10L, QuestionType.SINGLE_CHOICE);
        com.example.words.model.StudentPaperAnswer answer = new com.example.words.model.StudentPaperAnswer();
        answer.setAttemptId(100L);
        answer.setPaperReleaseId(10L);
        answer.setReleaseQuestionId(1000L);
        answer.setSelectedAnswersJson("[\"B\"]");
        answer.setCorrect(true);
        answer.setEarnedScore(new BigDecimal("5.00"));
        stubLockedAttempt(attempt, release);
        when(releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(10L))
                .thenReturn(List.of(question));
        when(answerRepository.findByAttemptId(100L)).thenReturn(List.of(answer));

        StudentPaperAttemptResponse response = service.open(100L, student);

        assertEquals(List.of("B"), response.getAnswers().get(0).getSelectedAnswers());
        assertFalse(response.getAnswers().get(0).getClass().getDeclaredFields().length > 3);
    }

    @Test
    void finalSubmitGradesAllSupportedQuestionTypesAndLocksRows() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        PaperReleaseQuestion single = scoredQuestion(
                1000L, 10L, QuestionType.SINGLE_CHOICE, "[\"A\"]", "2.00");
        PaperReleaseQuestion multiple = scoredQuestion(
                1001L, 10L, QuestionType.MULTIPLE_CHOICE, "[\"A\",\"C\"]", "3.00");
        PaperReleaseQuestion blank = scoredQuestion(
                1002L, 10L, QuestionType.FILL_IN_BLANK, "[\"word\",\"term\"]", "5.00");
        stubSubmit(attempt, release, List.of(single, multiple, blank), List.of());
        SubmitStudentPaperRequest request = new SubmitStudentPaperRequest(
                3L,
                List.of(
                        new StudentPaperAnswerRequest(1000L, List.of(" a "), List.of()),
                        new StudentPaperAnswerRequest(1001L, List.of("C", "a"), List.of()),
                        new StudentPaperAnswerRequest(1002L, List.of(), List.of(" WORD "))));

        SubmitStudentPaperResponse response = service.submit(100L, request, student);

        assertEquals(StudentPaperAttemptStatus.SUBMITTED, attempt.getStatus());
        assertEquals(new BigDecimal("10.00"), attempt.getEarnedScore());
        assertEquals(3, attempt.getAnsweredCount());
        assertEquals(3, attempt.getCorrectCount());
        assertEquals(NOW, attempt.getSubmittedAt());
        assertFalse(response.getIdempotent());
        verify(answerRepository).saveAllAndFlush(argThat(answers -> {
            List<com.example.words.model.StudentPaperAnswer> rows =
                    (List<com.example.words.model.StudentPaperAnswer>) answers;
            return rows.size() == 3
                    && rows.stream().allMatch(answer -> Boolean.TRUE.equals(answer.getCorrect()))
                    && rows.stream().allMatch(answer -> NOW.equals(answer.getFinalizedAt()));
        }));
    }

    @Test
    void multipleChoiceRequiresExactSetAndFillAcceptsAnyTrimmedCaseInsensitiveVariant() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        PaperReleaseQuestion multiple = scoredQuestion(
                1000L, 10L, QuestionType.MULTIPLE_CHOICE, "[\"A\",\"C\"]", "2.00");
        PaperReleaseQuestion blank = scoredQuestion(
                1001L, 10L, QuestionType.FILL_IN_BLANK, "[\"colour\",\"color\"]", "3.00");
        stubSubmit(attempt, release, List.of(multiple, blank), List.of());

        service.submit(100L, new SubmitStudentPaperRequest(
                3L,
                List.of(
                        new StudentPaperAnswerRequest(1000L, List.of("A"), List.of()),
                        new StudentPaperAnswerRequest(1001L, List.of(), List.of(" COLOR ")))), student);

        assertEquals(new BigDecimal("3.00"), attempt.getEarnedScore());
        assertEquals(1, attempt.getCorrectCount());
    }

    @Test
    void fillInBlankRejectsMultipleSubmittedValuesInsteadOfMatchingAnyOne() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        PaperReleaseQuestion blank = scoredQuestion(
                1000L, 10L, QuestionType.FILL_IN_BLANK, "[\"colour\",\"color\"]", "5.00");
        stubSubmit(attempt, release, List.of(blank), List.of());

        assertThrows(BadRequestException.class, () -> service.submit(
                100L,
                new SubmitStudentPaperRequest(
                        3L,
                        List.of(new StudentPaperAnswerRequest(
                                1000L, List.of(), List.of("wrong", "color")))),
                student));

        verify(answerRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void allowBlankFinalizesMissingQuestionAtZero() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        PaperReleaseQuestion first = scoredQuestion(
                1000L, 10L, QuestionType.SINGLE_CHOICE, "[\"A\"]", "2.00");
        PaperReleaseQuestion second = scoredQuestion(
                1001L, 10L, QuestionType.FILL_IN_BLANK, "[\"word\"]", "3.00");
        stubSubmit(attempt, release, List.of(first, second), List.of());

        service.submit(100L, new SubmitStudentPaperRequest(
                3L,
                List.of(new StudentPaperAnswerRequest(1000L, List.of("B"), List.of()))), student);

        assertEquals(1, attempt.getAnsweredCount());
        assertEquals(0, attempt.getCorrectCount());
        assertEquals(new BigDecimal("0.00"), attempt.getEarnedScore());
        verify(answerRepository).saveAllAndFlush(argThat(answers -> {
            List<com.example.words.model.StudentPaperAnswer> rows =
                    (List<com.example.words.model.StudentPaperAnswer>) answers;
            return rows.size() == 2
                    && rows.stream().allMatch(answer -> BigDecimal.ZERO.compareTo(answer.getEarnedScore()) == 0)
                    && rows.stream().allMatch(answer -> Boolean.FALSE.equals(answer.getCorrect()));
        }));
    }

    @Test
    void requireAllAnsweredRejectsBlankFinalSubmissionWithoutLockingScores() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        release.setBlankAnswerPolicy(PaperBlankAnswerPolicy.REQUIRE_ALL_ANSWERED);
        PaperReleaseQuestion first = scoredQuestion(
                1000L, 10L, QuestionType.SINGLE_CHOICE, "[\"A\"]", "2.00");
        PaperReleaseQuestion second = scoredQuestion(
                1001L, 10L, QuestionType.FILL_IN_BLANK, "[\"word\"]", "3.00");
        stubSubmit(attempt, release, List.of(first, second), List.of());

        assertThrows(BadRequestException.class, () -> service.submit(
                100L,
                new SubmitStudentPaperRequest(
                        3L,
                        List.of(new StudentPaperAnswerRequest(1000L, List.of("A"), List.of()))),
                student));

        assertEquals(StudentPaperAttemptStatus.IN_PROGRESS, attempt.getStatus());
        verify(answerRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void duplicateSubmitIsIdempotentEvenWithStaleVersionAndDifferentAnswers() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.SUBMITTED);
        attempt.setEarnedScore(new BigDecimal("4.00"));
        attempt.setCorrectCount(1);
        attempt.setAnsweredCount(1);
        attempt.setSubmittedAt(NOW.minusMinutes(2));
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        stubLockedAttempt(attempt, release);

        SubmitStudentPaperResponse response = service.submit(
                100L,
                new SubmitStudentPaperRequest(
                        1L,
                        List.of(new StudentPaperAnswerRequest(9999L, List.of("B"), List.of()))),
                student);

        assertTrue(response.getIdempotent());
        assertEquals(new BigDecimal("4.00"), response.getResult().getEarnedScore());
        verify(releaseQuestionRepository, never()).findByPaperReleaseIdOrderByQuestionOrderAsc(10L);
        verify(answerRepository, never()).saveAllAndFlush(anyList());
        verify(attemptRepository, never()).saveAndFlush(attempt);
    }

    @Test
    void deadlineIsInclusiveAndLateSubmissionRemainsAllowed() {
        StudentPaperAttempt onTime = attempt(100L, 10L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease deadlineNow = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW);
        stubSubmit(onTime, deadlineNow, List.of(
                scoredQuestion(1000L, 10L, QuestionType.SINGLE_CHOICE, "[\"A\"]", "5.00")), List.of());
        service.submit(100L, new SubmitStudentPaperRequest(3L, List.of()), student);
        assertEquals(StudentPaperAttemptStatus.SUBMITTED, onTime.getStatus());

        StudentPaperAttempt late = attempt(101L, 11L, StudentPaperAttemptStatus.IN_PROGRESS);
        PaperRelease overdue = release(11L, PaperReleaseStatus.OPEN, NOW.minusHours(2), NOW.minusNanos(1));
        when(attemptRepository.findPaperReleaseIdByIdAndStudentId(101L, student.getId()))
                .thenReturn(Optional.of(11L));
        when(releaseRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(overdue));
        when(attemptRepository.findByIdAndStudentIdForUpdate(101L, student.getId()))
                .thenReturn(Optional.of(late));
        when(releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(11L))
                .thenReturn(List.of(scoredQuestion(
                        1100L, 11L, QuestionType.SINGLE_CHOICE, "[\"A\"]", "5.00")));
        when(answerRepository.findByAttemptId(101L)).thenReturn(List.of());

        service.submit(101L, new SubmitStudentPaperRequest(3L, List.of()), student);

        assertEquals(StudentPaperAttemptStatus.SUBMITTED_LATE, late.getStatus());
    }

    @Test
    void resultVisibilityHidesEverythingUntilReleasedAndScoreOnlyNeverLeaksAnswers() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.SUBMITTED);
        attempt.setEarnedScore(new BigDecimal("4.00"));
        attempt.setScorePercentage(new BigDecimal("80.00"));
        PaperRelease hidden = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW.plusHours(1));
        hidden.setResultVisibility(PaperResultVisibility.HIDDEN_UNTIL_RELEASED);
        when(attemptRepository.findByIdAndStudentId(100L, student.getId())).thenReturn(Optional.of(attempt));
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(hidden));

        StudentPaperResultResponse held = service.getResult(100L, student);

        assertFalse(held.getScoreVisible());
        assertFalse(held.getAnswersVisible());
        assertNull(held.getEarnedScore());
        assertTrue(held.getQuestions().isEmpty());
        verify(releaseQuestionRepository, never()).findByPaperReleaseIdOrderByQuestionOrderAsc(10L);

        hidden.setResultVisibility(PaperResultVisibility.SCORE_ONLY);
        StudentPaperResultResponse scoreOnly = service.getResult(100L, student);
        assertTrue(scoreOnly.getScoreVisible());
        assertFalse(scoreOnly.getAnswersVisible());
        assertEquals(new BigDecimal("4.00"), scoreOnly.getEarnedScore());
        assertTrue(scoreOnly.getQuestions().isEmpty());
    }

    @Test
    void scoreAndAnswersResultIncludesCorrectAnswersAndExplanationOnlyAfterSubmission() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.SUBMITTED_LATE);
        attempt.setEarnedScore(new BigDecimal("5.00"));
        PaperRelease release = release(10L, PaperReleaseStatus.OPEN, NOW.minusHours(2), NOW.minusHours(1));
        release.setResultVisibility(PaperResultVisibility.SCORE_AND_ANSWERS);
        PaperReleaseQuestion question = scoredQuestion(
                1000L, 10L, QuestionType.FILL_IN_BLANK, "[\"word\",\"term\"]", "5.00");
        com.example.words.model.StudentPaperAnswer answer = new com.example.words.model.StudentPaperAnswer();
        answer.setReleaseQuestionId(1000L);
        answer.setBlankAnswersJson("[\"Word\"]");
        answer.setSelectedAnswersJson("[]");
        answer.setCorrect(true);
        answer.setEarnedScore(new BigDecimal("5.00"));
        when(attemptRepository.findByIdAndStudentId(100L, student.getId())).thenReturn(Optional.of(attempt));
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release));
        when(releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(10L))
                .thenReturn(List.of(question));
        when(answerRepository.findByAttemptId(100L)).thenReturn(List.of(answer));

        StudentPaperResultResponse result = service.getResult(100L, student);

        assertTrue(result.getScoreVisible());
        assertTrue(result.getAnswersVisible());
        assertEquals(List.of("word", "term"), result.getQuestions().get(0).getAcceptedAnswers());
        assertEquals(Map.of("A", "Alpha", "B", "Beta", "C", "Gamma"),
                result.getQuestions().get(0).getOptions());
        assertEquals("A is correct", result.getQuestions().get(0).getExplanation());
        assertEquals(List.of("Word"), result.getQuestions().get(0).getBlankAnswers());
    }

    @Test
    void hiddenSupersededReleaseCannotBeOpenedThroughDirectResultLookup() {
        StudentPaperAttempt attempt = attempt(100L, 10L, StudentPaperAttemptStatus.SUBMITTED);
        PaperRelease release = release(10L, PaperReleaseStatus.SUPERSEDED, NOW.minusHours(2), NOW.minusHours(1));
        release.setResultVisibility(PaperResultVisibility.SCORE_AND_ANSWERS);
        release.setShowSupersededToStudents(false);
        when(attemptRepository.findByIdAndStudentId(100L, student.getId())).thenReturn(Optional.of(attempt));
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release));

        assertThrows(AccessDeniedException.class, () -> service.getResult(100L, student));

        verify(releaseQuestionRepository, never()).findByPaperReleaseIdOrderByQuestionOrderAsc(10L);
        verify(answerRepository, never()).findByAttemptId(100L);
    }

    private void stubLockedAttempt(StudentPaperAttempt attempt, PaperRelease release) {
        when(attemptRepository.findPaperReleaseIdByIdAndStudentId(attempt.getId(), student.getId()))
                .thenReturn(Optional.of(release.getId()));
        when(releaseRepository.findByIdForUpdate(release.getId())).thenReturn(Optional.of(release));
        when(attemptRepository.findByIdAndStudentIdForUpdate(attempt.getId(), student.getId()))
                .thenReturn(Optional.of(attempt));
    }

    private void stubSubmit(
            StudentPaperAttempt attempt,
            PaperRelease release,
            List<PaperReleaseQuestion> questions,
            List<com.example.words.model.StudentPaperAnswer> answers) {
        stubLockedAttempt(attempt, release);
        when(releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(release.getId()))
                .thenReturn(questions);
        when(answerRepository.findByAttemptId(attempt.getId())).thenReturn(answers);
    }

    private PaperRelease release(
            Long id, PaperReleaseStatus status, LocalDateTime startTime, LocalDateTime deadline) {
        PaperRelease release = new PaperRelease();
        release.setId(id);
        release.setTitle("Vocabulary check");
        release.setInstructions("Choose carefully");
        release.setStatus(status);
        release.setQuestionCount(1);
        release.setTotalScore(new BigDecimal("5.00"));
        release.setStartTime(startTime);
        release.setDeadline(deadline);
        release.setBlankAnswerPolicy(PaperBlankAnswerPolicy.ALLOW_BLANK);
        release.setResultVisibility(PaperResultVisibility.SCORE_ONLY);
        release.setShowSupersededToStudents(false);
        return release;
    }

    private StudentPaperAttempt attempt(Long id, Long releaseId, StudentPaperAttemptStatus status) {
        StudentPaperAttempt attempt = new StudentPaperAttempt();
        attempt.setId(id);
        attempt.setPaperReleaseId(releaseId);
        attempt.setStudentId(student == null ? 20L : student.getId());
        attempt.setStatus(status);
        attempt.setVersion(3L);
        attempt.setAnsweredCount(0);
        attempt.setCorrectCount(0);
        attempt.setEarnedScore(BigDecimal.ZERO);
        attempt.setTotalScore(new BigDecimal("5.00"));
        return attempt;
    }

    private PaperReleaseQuestion question(Long id, Long releaseId, QuestionType type) {
        PaperReleaseQuestion question = new PaperReleaseQuestion();
        question.setId(id);
        question.setPaperReleaseId(releaseId);
        question.setQuestionOrder(1);
        question.setQuestionType(type);
        question.setStem("Pick one");
        question.setOptionsJson("{\"A\":\"Alpha\",\"B\":\"Beta\",\"C\":\"Gamma\"}");
        question.setAcceptedAnswersJson("[\"A\"]");
        question.setExplanation("A is correct");
        question.setScore(new BigDecimal("5.00"));
        return question;
    }

    private PaperReleaseQuestion scoredQuestion(
            Long id, Long releaseId, QuestionType type, String acceptedAnswers, String score) {
        PaperReleaseQuestion question = question(id, releaseId, type);
        question.setAcceptedAnswersJson(acceptedAnswers);
        question.setScore(new BigDecimal(score));
        return question;
    }

    private AppUser user(Long id, UserRole role) {
        AppUser actor = new AppUser();
        actor.setId(id);
        actor.setRole(role);
        return actor;
    }

    private static Stream<Arguments> malformedAnswerShapes() {
        return Stream.of(
                Arguments.of("single choice with two keys", QuestionType.SINGLE_CHOICE,
                        List.of("A", "B"), List.of()),
                Arguments.of("single choice with unknown key", QuestionType.SINGLE_CHOICE,
                        List.of("Z"), List.of()),
                Arguments.of("single choice with blank value", QuestionType.SINGLE_CHOICE,
                        List.of("A"), List.of("text")),
                Arguments.of("multiple choice with duplicate key", QuestionType.MULTIPLE_CHOICE,
                        List.of("A", "a"), List.of()),
                Arguments.of("multiple choice with unknown key", QuestionType.MULTIPLE_CHOICE,
                        List.of("A", "Z"), List.of()),
                Arguments.of("multiple choice with blank value", QuestionType.MULTIPLE_CHOICE,
                        List.of("A"), List.of("text")),
                Arguments.of("fill-in with option key", QuestionType.FILL_IN_BLANK,
                        List.of("A"), List.of("word")),
                Arguments.of("fill-in with two values", QuestionType.FILL_IN_BLANK,
                        List.of(), List.of("word", "term")));
    }

    private String acceptedAnswers(QuestionType type) {
        return switch (type) {
            case SINGLE_CHOICE -> "[\"A\"]";
            case MULTIPLE_CHOICE -> "[\"A\",\"C\"]";
            case FILL_IN_BLANK -> "[\"word\",\"term\"]";
        };
    }
}
