package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.words.dto.PaperReleaseQuestionStatResponse;
import com.example.words.dto.PaperReleaseResponse;
import com.example.words.dto.PaperReleaseResultOverviewResponse;
import com.example.words.dto.PaperReleaseStudentResultResponse;
import com.example.words.dto.ReleasePaperResultsRequest;
import com.example.words.dto.StudentPaperResultResponse;
import com.example.words.exception.ConflictException;
import com.example.words.model.AppUser;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperReleaseQuestion;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.PaperReleaseTarget;
import com.example.words.model.PaperResultVisibility;
import com.example.words.model.QuestionType;
import com.example.words.model.StudentPaperAnswer;
import com.example.words.model.StudentPaperAttempt;
import com.example.words.model.StudentPaperAttemptStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.PaperReleaseQuestionRepository;
import com.example.words.repository.PaperReleaseRepository;
import com.example.words.repository.PaperReleaseTargetRepository;
import com.example.words.repository.StudentPaperAnswerRepository;
import com.example.words.repository.StudentPaperAttemptRepository;
import com.example.words.repository.TeacherStudentRelationRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperResultReviewServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 10, 0);

    @Mock
    private PaperReleaseRepository releaseRepository;

    @Mock
    private PaperReleaseQuestionRepository questionRepository;

    @Mock
    private StudentPaperAttemptRepository attemptRepository;

    @Mock
    private StudentPaperAnswerRepository answerRepository;

    @Mock
    private PaperReleaseTargetRepository targetRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassroomMemberRepository classroomMemberRepository;

    @Mock
    private TeacherStudentRelationRepository relationRepository;

    @Mock
    private AppUserRepository appUserRepository;

    private PaperResultReviewService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-29T02:00:00Z"), ZoneOffset.ofHours(8));
        service = new PaperResultReviewService(
                releaseRepository,
                questionRepository,
                attemptRepository,
                answerRepository,
                targetRepository,
                classroomRepository,
                classroomMemberRepository,
                relationRepository,
                appUserRepository,
                new ObjectMapper(),
                clock);
    }

    @Test
    void releaseListIncludesPublisherAndCurrentTargetClassTeacherWithoutLeakingOtherReleases() {
        AppUser teacher = user(7L, UserRole.TEACHER);
        PaperRelease published = release(9L, 7L, PaperReleaseStatus.OPEN, NOW.plusHours(1));
        PaperRelease classRelease = release(10L, 5L, PaperReleaseStatus.OPEN, NOW.plusHours(1));
        PaperRelease unrelated = release(11L, 6L, PaperReleaseStatus.OPEN, NOW.plusHours(1));
        PaperReleaseTarget classTarget = target(20L, "[31]");
        classTarget.setPaperReleaseId(10L);
        PaperReleaseTarget unrelatedTarget = target(21L, "[32]");
        unrelatedTarget.setPaperReleaseId(11L);
        StudentPaperAttempt classAttempt = attempt(101L, 20L, StudentPaperAttemptStatus.NOT_STARTED);

        when(releaseRepository.findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(unrelated, classRelease, published));
        when(targetRepository.findByPaperReleaseId(9L)).thenReturn(List.of());
        when(targetRepository.findByPaperReleaseId(10L)).thenReturn(List.of(classTarget));
        when(targetRepository.findByPaperReleaseId(11L)).thenReturn(List.of(unrelatedTarget));
        when(attemptRepository.findByPaperReleaseIdOrderByStudentIdAsc(9L)).thenReturn(List.of());
        when(attemptRepository.findByPaperReleaseIdOrderByStudentIdAsc(10L)).thenReturn(List.of(classAttempt));
        when(classroomRepository.findIdsByTeacherId(7L)).thenReturn(List.of(31L));
        when(relationRepository.findStudentIdsByTeacherIdAndStudentIdIn(eq(7L), anyCollection()))
                .thenReturn(List.of());

        List<PaperReleaseResponse> result = service.listReleases(teacher);

        assertEquals(List.of(10L, 9L), result.stream().map(PaperReleaseResponse::getId).toList());
        assertEquals(List.of(20L), result.get(0).getTargets().stream()
                .map(target -> target.getStudentId()).toList());
        verify(attemptRepository, never()).findByPaperReleaseIdOrderByStudentIdAsc(11L);
    }

    @Test
    void releaseDetailFiltersTargetsForCurrentClassTeacherAndDeniesUnrelatedTeacher() {
        PaperRelease release = release(10L, 5L, PaperReleaseStatus.OPEN, NOW.plusHours(1));
        PaperReleaseTarget ownedClassTarget = target(20L, "[31]");
        ownedClassTarget.setSourceClassroomId(31L);
        ownedClassTarget.setSourceClassroomIdsJson(null);
        PaperReleaseTarget otherClassTarget = target(21L, "[32]");
        StudentPaperAttempt ownedAttempt = attempt(101L, 20L, StudentPaperAttemptStatus.SUBMITTED);
        StudentPaperAttempt otherAttempt = attempt(102L, 21L, StudentPaperAttemptStatus.SUBMITTED);
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release));
        when(targetRepository.findByPaperReleaseId(10L))
                .thenReturn(List.of(ownedClassTarget, otherClassTarget));
        when(attemptRepository.findByPaperReleaseIdOrderByStudentIdAsc(10L))
                .thenReturn(List.of(ownedAttempt, otherAttempt));
        when(classroomRepository.findIdsByTeacherId(7L)).thenReturn(List.of(31L));
        when(relationRepository.findStudentIdsByTeacherIdAndStudentIdIn(eq(7L), anyCollection()))
                .thenReturn(List.of());

        PaperReleaseResponse visible = service.getRelease(10L, user(7L, UserRole.TEACHER));

        assertEquals(List.of(20L), visible.getTargets().stream()
                .map(target -> target.getStudentId()).toList());
        assertEquals(List.of(31L), visible.getTargets().get(0).getSourceClassroomIds());

        when(classroomRepository.findIdsByTeacherId(8L)).thenReturn(List.of(33L));
        when(relationRepository.findStudentIdsByTeacherIdAndStudentIdIn(eq(8L), anyCollection()))
                .thenReturn(List.of());
        assertThrows(AccessDeniedException.class,
                () -> service.getRelease(10L, user(8L, UserRole.TEACHER)));
    }

    @Test
    void overviewIncludesTargetStudentCurrentlyInAnyClassroomOwnedByReviewingTeacher() {
        AppUser currentClassTeacher = user(7L, UserRole.TEACHER);
        PaperRelease release = release(10L, 5L, PaperReleaseStatus.OPEN, NOW.plusHours(1));
        StudentPaperAttempt transferredStudent = attempt(101L, 20L, StudentPaperAttemptStatus.NOT_STARTED);
        StudentPaperAttempt unrelatedStudent = attempt(102L, 21L, StudentPaperAttemptStatus.NOT_STARTED);
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release));
        when(attemptRepository.findByPaperReleaseIdOrderByStudentIdAsc(10L))
                .thenReturn(List.of(transferredStudent, unrelatedStudent));
        when(targetRepository.findByPaperReleaseId(10L)).thenReturn(List.of(
                target(20L, "[31]"),
                target(21L, "[31]")));
        when(classroomRepository.findIdsByTeacherId(7L)).thenReturn(List.of(41L));
        when(relationRepository.findStudentIdsByTeacherIdAndStudentIdIn(eq(7L), anyCollection()))
                .thenReturn(List.of());
        when(classroomMemberRepository.findStudentIdsByClassroomIdInAndStudentIdIn(
                eq(Set.of(41L)), anyCollection()))
                .thenReturn(List.of(20L));

        PaperReleaseResultOverviewResponse result = service.getOverview(10L, currentClassTeacher);

        assertEquals(List.of(20L), result.getStudents().stream()
                .map(PaperReleaseStudentResultResponse::getStudentId)
                .toList());
        verify(classroomMemberRepository, times(1))
                .findStudentIdsByClassroomIdInAndStudentIdIn(eq(Set.of(41L)), anyCollection());
        verify(classroomMemberRepository, never()).existsByClassroomIdInAndStudentId(
                anyCollection(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void overviewFiltersToStudentsActorCanReviewAndDerivesEffectiveOverdue() {
        AppUser responsibleTeacher = user(7L, UserRole.TEACHER);
        PaperRelease release = release(10L, 5L, PaperReleaseStatus.OPEN, NOW.minusMinutes(1));
        StudentPaperAttempt overdue = attempt(101L, 20L, StudentPaperAttemptStatus.NOT_STARTED);
        StudentPaperAttempt submitted = attempt(102L, 21L, StudentPaperAttemptStatus.SUBMITTED);
        StudentPaperAttempt unrelatedLate = attempt(103L, 22L, StudentPaperAttemptStatus.SUBMITTED_LATE);
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release));
        when(attemptRepository.findByPaperReleaseIdOrderByStudentIdAsc(10L))
                .thenReturn(List.of(overdue, submitted, unrelatedLate));
        when(targetRepository.findByPaperReleaseId(10L)).thenReturn(List.of(
                target(20L, "[31]"),
                target(21L, "[]"),
                target(22L, "[32]")));
        when(classroomRepository.findIdsByTeacherId(7L)).thenReturn(List.of(31L));
        when(relationRepository.findStudentIdsByTeacherIdAndStudentIdIn(eq(7L), anyCollection()))
                .thenReturn(List.of(21L));
        AppUser student20 = user(20L, UserRole.STUDENT);
        student20.setUsername("student_api_20");
        AppUser student21 = user(21L, UserRole.STUDENT);
        student21.setUsername("student_api_21");
        when(appUserRepository.findAllById(anyCollection())).thenReturn(List.of(student20, student21));

        PaperReleaseResultOverviewResponse result = service.getOverview(10L, responsibleTeacher);

        assertEquals(2, result.getAssignedCount());
        assertEquals(1, result.getOverdueCount());
        assertEquals(1, result.getSubmittedCount());
        assertEquals(0, result.getSubmittedLateCount());
        assertEquals(1, result.getCompletedCount());
        assertEquals(List.of(101L, 102L), result.getStudents().stream()
                .map(PaperReleaseStudentResultResponse::getAttemptId)
                .toList());
        assertEquals(List.of("student_api_20", "student_api_21"), result.getStudents().stream()
                .map(PaperReleaseStudentResultResponse::getStudentUsername)
                .toList());
        assertEquals(StudentPaperAttemptStatus.OVERDUE, result.getStudents().get(0).getStatus());
        assertTrue(result.getStudents().get(0).getQuestions().isEmpty());
        verify(targetRepository).findByPaperReleaseId(10L);
        verify(classroomRepository).findIdsByTeacherId(7L);
        verify(relationRepository).findStudentIdsByTeacherIdAndStudentIdIn(eq(7L), anyCollection());
        verify(relationRepository, never()).existsByTeacherIdAndStudentId(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void overviewDeniesActorWhoCannotReviewAnyTargetAndRejectsInvalidReleaseByDefault() {
        AppUser unrelatedTeacher = user(7L, UserRole.TEACHER);
        PaperRelease open = release(10L, 5L, PaperReleaseStatus.OPEN, NOW.plusHours(1));
        StudentPaperAttempt attempt = attempt(101L, 20L, StudentPaperAttemptStatus.NOT_STARTED);
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(open));
        when(attemptRepository.findByPaperReleaseIdOrderByStudentIdAsc(10L)).thenReturn(List.of(attempt));
        when(targetRepository.findByPaperReleaseId(10L)).thenReturn(List.of(target(20L, "[31]")));
        when(classroomRepository.findIdsByTeacherId(7L)).thenReturn(List.of(32L));
        when(relationRepository.findStudentIdsByTeacherIdAndStudentIdIn(eq(7L), anyCollection()))
                .thenReturn(List.of());

        assertThrows(AccessDeniedException.class, () -> service.getOverview(10L, unrelatedTeacher));

        PaperRelease invalidated = release(11L, 5L, PaperReleaseStatus.INVALIDATED, NOW.plusHours(1));
        when(releaseRepository.findById(11L)).thenReturn(Optional.of(invalidated));
        when(attemptRepository.findByPaperReleaseIdOrderByStudentIdAsc(11L)).thenReturn(List.of(attempt));
        when(targetRepository.findByPaperReleaseId(11L)).thenReturn(List.of(target(20L, "[31]")));
        assertThrows(AccessDeniedException.class,
                () -> service.getOverview(11L, unrelatedTeacher));
        assertThrows(ConflictException.class, () -> service.getOverview(11L, user(5L, UserRole.TEACHER)));
    }

    @Test
    void statisticsAuthorizeBeforeRejectingWithdrawnReleaseState() {
        AppUser unrelatedTeacher = user(7L, UserRole.TEACHER);
        PaperRelease withdrawn = release(10L, 5L, PaperReleaseStatus.WITHDRAWN, NOW.plusHours(1));
        StudentPaperAttempt attempt = attempt(101L, 20L, StudentPaperAttemptStatus.NOT_STARTED);
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(withdrawn));
        when(attemptRepository.findByPaperReleaseIdOrderByStudentIdAsc(10L)).thenReturn(List.of(attempt));
        when(targetRepository.findByPaperReleaseId(10L)).thenReturn(List.of(target(20L, "[31]")));
        when(classroomRepository.findIdsByTeacherId(7L)).thenReturn(List.of(32L));
        when(relationRepository.findStudentIdsByTeacherIdAndStudentIdIn(eq(7L), anyCollection()))
                .thenReturn(List.of());

        assertThrows(AccessDeniedException.class,
                () -> service.getQuestionStatistics(10L, unrelatedTeacher));
        assertThrows(ConflictException.class,
                () -> service.getQuestionStatistics(10L, user(5L, UserRole.TEACHER)));
        verify(questionRepository, never()).findByPaperReleaseIdOrderByQuestionOrderAsc(10L);
        verify(answerRepository, never()).findByAttemptIdIn(anyCollection());
    }

    @Test
    void studentDetailUsesFrozenRowsAndChecksTheTargetStudentPermission() {
        AppUser publisher = user(5L, UserRole.TEACHER);
        PaperRelease release = release(10L, 5L, PaperReleaseStatus.OPEN, NOW.plusHours(1));
        StudentPaperAttempt attempt = attempt(101L, 20L, StudentPaperAttemptStatus.SUBMITTED_LATE);
        attempt.setAnsweredCount(1);
        attempt.setCorrectCount(1);
        attempt.setEarnedScore(new BigDecimal("4.00"));
        attempt.setTotalScore(new BigDecimal("5.00"));
        attempt.setScorePercentage(new BigDecimal("80.00"));
        PaperReleaseQuestion question = question(301L, 10L);
        StudentPaperAnswer answer = answer(401L, 101L, 301L, true, "4.00");
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release));
        when(attemptRepository.findById(101L)).thenReturn(Optional.of(attempt));
        when(questionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(10L)).thenReturn(List.of(question));
        when(answerRepository.findByAttemptIdIn(List.of(101L))).thenReturn(List.of(answer));

        PaperReleaseStudentResultResponse result = service.getStudentResult(10L, 101L, publisher);

        assertEquals(20L, result.getStudentId());
        assertEquals(StudentPaperAttemptStatus.SUBMITTED_LATE, result.getStatus());
        assertTrue(result.getLate());
        assertEquals(new BigDecimal("4.00"), result.getEarnedScore());
        assertEquals(List.of("A"), result.getQuestions().get(0).getSelectedAnswers());
        assertEquals(List.of("A"), result.getQuestions().get(0).getAcceptedAnswers());
        assertEquals(Map.of("A", "Alpha", "B", "Beta"), result.getQuestions().get(0).getOptions());
        assertEquals("Frozen explanation", result.getQuestions().get(0).getExplanation());
        verify(targetRepository, never()).findByPaperReleaseId(10L);
        verify(answerRepository).findByAttemptIdIn(List.of(101L));
        verify(answerRepository, never()).findByAttemptId(101L);
    }

    @Test
    void studentDetailDeniesUnrelatedTeachersAndStudentActorsBeforeReadingAnswers() {
        AppUser unrelatedTeacher = user(7L, UserRole.TEACHER);
        AppUser studentActor = user(21L, UserRole.STUDENT);
        PaperRelease release = release(10L, 5L, PaperReleaseStatus.OPEN, NOW.plusHours(1));
        StudentPaperAttempt attempt = attempt(101L, 20L, StudentPaperAttemptStatus.SUBMITTED);
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release));
        when(attemptRepository.findById(101L)).thenReturn(Optional.of(attempt));
        when(targetRepository.findByPaperReleaseId(10L)).thenReturn(List.of(target(20L, "[31]")));
        when(classroomRepository.findIdsByTeacherId(7L)).thenReturn(List.of(32L));
        when(relationRepository.findStudentIdsByTeacherIdAndStudentIdIn(eq(7L), anyCollection()))
                .thenReturn(List.of());

        assertThrows(AccessDeniedException.class,
                () -> service.getStudentResult(10L, 101L, unrelatedTeacher));
        assertThrows(AccessDeniedException.class,
                () -> service.getStudentResult(10L, 101L, studentActor));
        verify(answerRepository, never()).findByAttemptId(101L);
        verify(questionRepository, never()).findByPaperReleaseIdOrderByQuestionOrderAsc(10L);
    }

    @Test
    void questionStatisticsUseOnlyVisibleValidFinalSubmissionsWithPreciseRate() {
        AppUser publisher = user(5L, UserRole.TEACHER);
        PaperRelease release = release(10L, 5L, PaperReleaseStatus.OPEN, NOW.minusHours(1));
        PaperReleaseQuestion question = question(301L, 10L);
        StudentPaperAttempt correct = attempt(101L, 20L, StudentPaperAttemptStatus.SUBMITTED);
        StudentPaperAttempt wrongLate = attempt(102L, 21L, StudentPaperAttemptStatus.SUBMITTED_LATE);
        StudentPaperAttempt overdue = attempt(103L, 22L, StudentPaperAttemptStatus.NOT_STARTED);
        StudentPaperAttempt invalidated = attempt(104L, 23L, StudentPaperAttemptStatus.INVALIDATED);
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release));
        when(questionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(10L)).thenReturn(List.of(question));
        when(attemptRepository.findByPaperReleaseIdOrderByStudentIdAsc(10L))
                .thenReturn(List.of(correct, wrongLate, overdue, invalidated));
        when(answerRepository.findByAttemptIdIn(List.of(101L, 102L))).thenReturn(List.of(
                answer(401L, 101L, 301L, true, "5.00"),
                answer(402L, 102L, 301L, false, "0.00")));

        List<PaperReleaseQuestionStatResponse> result = service.getQuestionStatistics(10L, publisher);

        assertEquals(1, result.size());
        PaperReleaseQuestionStatResponse stat = result.get(0);
        assertEquals(2, stat.getSubmissionCount());
        assertEquals(2, stat.getAnsweredCount());
        assertEquals(1, stat.getCorrectCount());
        assertEquals(new BigDecimal("50.00"), stat.getCorrectnessRate());
        verify(answerRepository).findByAttemptIdIn(List.of(101L, 102L));
        verify(answerRepository, never()).findByAttemptId(101L);
        verify(answerRepository, never()).findByAttemptId(102L);
        verify(answerRepository, never()).findByAttemptId(103L);
        verify(answerRepository, never()).findByAttemptId(104L);
    }

    @Test
    void overviewCapturesClockOnceForCountsAndStudentSummaries() {
        Clock changingClock = mock(Clock.class);
        when(changingClock.getZone()).thenReturn(ZoneOffset.ofHours(8));
        when(changingClock.instant()).thenReturn(
                Instant.parse("2026-07-29T01:59:59Z"),
                Instant.parse("2026-07-29T02:00:01Z"));
        PaperResultReviewService changingClockService = new PaperResultReviewService(
                releaseRepository,
                questionRepository,
                attemptRepository,
                answerRepository,
                targetRepository,
                classroomRepository,
                classroomMemberRepository,
                relationRepository,
                appUserRepository,
                new ObjectMapper(),
                changingClock);
        AppUser publisher = user(5L, UserRole.TEACHER);
        PaperRelease release = release(10L, 5L, PaperReleaseStatus.OPEN, NOW);
        StudentPaperAttempt first = attempt(101L, 20L, StudentPaperAttemptStatus.NOT_STARTED);
        StudentPaperAttempt second = attempt(102L, 21L, StudentPaperAttemptStatus.NOT_STARTED);
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release));
        when(attemptRepository.findByPaperReleaseIdOrderByStudentIdAsc(10L))
                .thenReturn(List.of(first, second));

        PaperReleaseResultOverviewResponse result = changingClockService.getOverview(10L, publisher);

        assertEquals(2, result.getNotStartedCount());
        assertEquals(0, result.getOverdueCount());
        assertTrue(result.getStudents().stream()
                .allMatch(student -> student.getStatus() == StudentPaperAttemptStatus.NOT_STARTED));
        verify(changingClock, times(1)).instant();
    }

    @Test
    void releaseResultsIsPublisherOnlyAndNeverTouchesLockedScores() {
        AppUser publisher = user(5L, UserRole.TEACHER);
        PaperRelease release = release(10L, 5L, PaperReleaseStatus.OPEN, NOW.plusHours(1));
        release.setResultVisibility(PaperResultVisibility.HIDDEN_UNTIL_RELEASED);
        when(releaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(release));

        PaperReleaseResultOverviewResponse result = service.releaseResults(
                10L,
                new ReleasePaperResultsRequest(PaperResultVisibility.SCORE_AND_ANSWERS),
                publisher);

        assertEquals(PaperResultVisibility.SCORE_AND_ANSWERS, release.getResultVisibility());
        assertEquals(NOW, release.getResultsReleasedAt());
        assertEquals(5L, release.getResultsReleasedByUserId());
        assertEquals(PaperResultVisibility.SCORE_AND_ANSWERS, result.getResultVisibility());
        assertTrue(result.getResultsReleased());
        verify(releaseRepository).save(release);
        verify(attemptRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(answerRepository, never()).save(org.mockito.ArgumentMatchers.any());

        PaperRelease another = release(11L, 5L, PaperReleaseStatus.OPEN, NOW.plusHours(1));
        when(releaseRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(another));
        assertThrows(AccessDeniedException.class, () -> service.releaseResults(
                11L,
                new ReleasePaperResultsRequest(PaperResultVisibility.SCORE_ONLY),
                user(7L, UserRole.TEACHER)));

        PaperRelease invalidated = release(12L, 5L, PaperReleaseStatus.INVALIDATED, NOW.plusHours(1));
        when(releaseRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(invalidated));
        assertThrows(ConflictException.class, () -> service.releaseResults(
                12L,
                new ReleasePaperResultsRequest(PaperResultVisibility.SCORE_ONLY),
                publisher));
    }

    @Test
    void releasingResultsImmediatelyChangesTaskSevenFilteringWithoutRegrading() {
        AppUser publisher = user(5L, UserRole.TEACHER);
        AppUser student = user(20L, UserRole.STUDENT);
        PaperRelease release = release(10L, 5L, PaperReleaseStatus.OPEN, NOW.plusHours(1));
        release.setResultVisibility(PaperResultVisibility.HIDDEN_UNTIL_RELEASED);
        StudentPaperAttempt submitted = attempt(101L, 20L, StudentPaperAttemptStatus.SUBMITTED);
        submitted.setAnsweredCount(1);
        submitted.setCorrectCount(1);
        submitted.setEarnedScore(new BigDecimal("5.00"));
        submitted.setScorePercentage(new BigDecimal("100.00"));
        PaperReleaseQuestion question = question(301L, 10L);
        StudentPaperAnswer answer = answer(401L, 101L, 301L, true, "5.00");
        when(attemptRepository.findByIdAndStudentId(101L, 20L)).thenReturn(Optional.of(submitted));
        when(attemptRepository.findByPaperReleaseIdOrderByStudentIdAsc(10L)).thenReturn(List.of(submitted));
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release));
        when(releaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(release));
        when(questionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(10L)).thenReturn(List.of(question));
        when(answerRepository.findByAttemptId(101L)).thenReturn(List.of(answer));
        StudentPaperAttemptService studentService = new StudentPaperAttemptService(
                releaseRepository,
                questionRepository,
                attemptRepository,
                answerRepository,
                new ExamPaperAnswerNormalizer(),
                new ObjectMapper(),
                org.mockito.Mockito.mock(StudentPointEventPublisher.class),
                Clock.fixed(Instant.parse("2026-07-29T02:00:00Z"), ZoneOffset.ofHours(8)));

        StudentPaperResultResponse held = studentService.getResult(101L, student);
        service.releaseResults(
                10L,
                new ReleasePaperResultsRequest(PaperResultVisibility.SCORE_AND_ANSWERS),
                publisher);
        StudentPaperResultResponse released = studentService.getResult(101L, student);

        assertFalse(held.getScoreVisible());
        assertTrue(released.getScoreVisible());
        assertTrue(released.getAnswersVisible());
        assertEquals(new BigDecimal("5.00"), released.getEarnedScore());
        assertEquals(new BigDecimal("5.00"), submitted.getEarnedScore());
        assertEquals(new BigDecimal("5.00"), answer.getEarnedScore());
    }

    private PaperRelease release(
            Long id, Long publisherId, PaperReleaseStatus status, LocalDateTime deadline) {
        PaperRelease release = new PaperRelease();
        release.setId(id);
        release.setPublishedByUserId(publisherId);
        release.setTitle("Frozen paper");
        release.setStatus(status);
        release.setQuestionCount(1);
        release.setTotalScore(new BigDecimal("5.00"));
        release.setDeadline(deadline);
        release.setResultVisibility(PaperResultVisibility.SCORE_ONLY);
        return release;
    }

    private StudentPaperAttempt attempt(Long id, Long studentId, StudentPaperAttemptStatus status) {
        StudentPaperAttempt attempt = new StudentPaperAttempt();
        attempt.setId(id);
        attempt.setPaperReleaseId(10L);
        attempt.setStudentId(studentId);
        attempt.setStatus(status);
        attempt.setAnsweredCount(0);
        attempt.setCorrectCount(0);
        attempt.setEarnedScore(BigDecimal.ZERO);
        attempt.setTotalScore(new BigDecimal("5.00"));
        return attempt;
    }

    private PaperReleaseQuestion question(Long id, Long releaseId) {
        PaperReleaseQuestion question = new PaperReleaseQuestion();
        question.setId(id);
        question.setPaperReleaseId(releaseId);
        question.setQuestionOrder(1);
        question.setQuestionType(QuestionType.SINGLE_CHOICE);
        question.setStem("Frozen stem");
        question.setOptionsJson("{\"A\":\"Alpha\",\"B\":\"Beta\"}");
        question.setAcceptedAnswersJson("[\"A\"]");
        question.setExplanation("Frozen explanation");
        question.setScore(new BigDecimal("5.00"));
        return question;
    }

    private StudentPaperAnswer answer(
            Long id, Long attemptId, Long questionId, boolean correct, String earnedScore) {
        StudentPaperAnswer answer = new StudentPaperAnswer();
        answer.setId(id);
        answer.setAttemptId(attemptId);
        answer.setPaperReleaseId(10L);
        answer.setReleaseQuestionId(questionId);
        answer.setSelectedAnswersJson("[\"A\"]");
        answer.setBlankAnswersJson("[]");
        answer.setCorrect(correct);
        answer.setEarnedScore(new BigDecimal(earnedScore));
        return answer;
    }

    private PaperReleaseTarget target(Long studentId, String sourceClassroomIdsJson) {
        PaperReleaseTarget target = new PaperReleaseTarget();
        target.setPaperReleaseId(10L);
        target.setStudentId(studentId);
        target.setSourceClassroomIdsJson(sourceClassroomIdsJson);
        return target;
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
