package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import com.example.words.dto.StudentAssessmentStatus;
import com.example.words.dto.StudentAssessmentSummaryResponse;
import com.example.words.dto.StudentAssessmentType;
import com.example.words.model.AppUser;
import com.example.words.model.Dictionary;
import com.example.words.model.Exam;
import com.example.words.model.ExamStatus;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.PaperResultVisibility;
import com.example.words.model.StudentPaperAttempt;
import com.example.words.model.StudentPaperAttemptStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.DictionaryRepository;
import com.example.words.repository.ExamRepository;
import com.example.words.repository.PaperReleaseRepository;
import com.example.words.repository.StudentPaperAttemptRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAssessmentServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 10, 0);

    @Mock
    private ExamRepository examRepository;

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private StudentPaperAttemptRepository attemptRepository;

    @Mock
    private PaperReleaseRepository releaseRepository;

    private StudentAssessmentService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-29T02:00:00Z"), ZoneOffset.ofHours(8));
        service = new StudentAssessmentService(
                examRepository,
                dictionaryRepository,
                attemptRepository,
                releaseRepository,
                clock);
    }

    @Test
    void pendingAggregatesLegacyAndPaperAttemptsWithRoutingAndEffectiveStatuses() {
        AppUser student = user(7L, UserRole.STUDENT);
        Exam legacy = exam(1L, 31L, ExamStatus.GENERATED, NOW.minusDays(1), null);
        when(examRepository.findStudentAssessments(7L, Set.of(ExamStatus.GENERATED, ExamStatus.SUBMITTED)))
                .thenReturn(List.of(legacy));
        when(dictionaryRepository.findAllById(Set.of(31L)))
                .thenReturn(List.of(dictionary(31L, "四级核心词汇")));

        StudentPaperAttempt scheduled = attempt(101L, 11L, StudentPaperAttemptStatus.NOT_STARTED, NOW.minusHours(1));
        StudentPaperAttempt notStarted = attempt(102L, 12L, StudentPaperAttemptStatus.NOT_STARTED, NOW.minusHours(2));
        StudentPaperAttempt inProgress = attempt(103L, 13L, StudentPaperAttemptStatus.IN_PROGRESS, NOW.minusHours(3));
        StudentPaperAttempt overdue = attempt(104L, 14L, StudentPaperAttemptStatus.NOT_STARTED, NOW.minusHours(4));
        when(attemptRepository.findByStudentIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(scheduled, notStarted, inProgress, overdue));
        when(releaseRepository.findAllById(Set.of(11L, 12L, 13L, 14L))).thenReturn(List.of(
                release(11L, 51L, "明日测验", PaperReleaseStatus.SCHEDULED,
                        NOW.plusHours(1), NOW.plusDays(1)),
                release(12L, 52L, "基础测验", PaperReleaseStatus.SCHEDULED,
                        NOW.minusMinutes(1), NOW.plusHours(2)),
                release(13L, 53L, "进行中测验", PaperReleaseStatus.OPEN,
                        NOW.minusHours(1), NOW.plusHours(1)),
                release(14L, 54L, "逾期测验", PaperReleaseStatus.OPEN,
                        NOW.minusDays(2), NOW.minusSeconds(1))));

        List<StudentAssessmentSummaryResponse> result = service.listPending(student);

        Map<Long, StudentAssessmentSummaryResponse> byAssessmentId = result.stream()
                .collect(Collectors.toMap(StudentAssessmentSummaryResponse::getAssessmentId, Function.identity()));
        assertEquals(5, result.size());
        assertEquals(StudentAssessmentType.LEGACY_GENERATED_EXAM, byAssessmentId.get(1L).getAssessmentType());
        assertEquals(StudentAssessmentStatus.NOT_STARTED, byAssessmentId.get(1L).getStatus());
        assertEquals(false, byAssessmentId.get(1L).getScoreVisible());
        assertEquals(1L, byAssessmentId.get(1L).getLegacyExamId());
        assertNull(byAssessmentId.get(1L).getPaperAttemptId());
        assertEquals(31L, byAssessmentId.get(1L).getDictionaryId());
        assertEquals("四级核心词汇", byAssessmentId.get(1L).getTitle());
        assertNull(byAssessmentId.get(1L).getCorrectCount());
        assertNull(byAssessmentId.get(1L).getScorePercentage());

        assertEquals(StudentAssessmentStatus.SCHEDULED, byAssessmentId.get(101L).getStatus());
        assertEquals(StudentAssessmentStatus.NOT_STARTED, byAssessmentId.get(102L).getStatus());
        assertEquals(StudentAssessmentStatus.IN_PROGRESS, byAssessmentId.get(103L).getStatus());
        assertEquals(StudentAssessmentStatus.OVERDUE, byAssessmentId.get(104L).getStatus());
        assertEquals(StudentAssessmentType.PAPER_RELEASE_ATTEMPT, byAssessmentId.get(104L).getAssessmentType());
        assertEquals(104L, byAssessmentId.get(104L).getPaperAttemptId());
        assertEquals(14L, byAssessmentId.get(104L).getPaperReleaseId());
        assertEquals(54L, byAssessmentId.get(104L).getPaperTemplateId());

        assertEquals(104L, result.get(0).getAssessmentId());
        assertEquals(101L, result.get(result.size() - 1).getAssessmentId());
        verify(examRepository, times(1))
                .findStudentAssessments(7L, Set.of(ExamStatus.GENERATED, ExamStatus.SUBMITTED));
        verify(attemptRepository, times(1)).findByStudentIdOrderByCreatedAtDesc(7L);
        verify(releaseRepository, times(1)).findAllById(Set.of(11L, 12L, 13L, 14L));
        verify(dictionaryRepository, times(1)).findAllById(Set.of(31L));
    }

    @Test
    void pendingAndHistoryExcludeInvalidReleaseAndHiddenSupersededRows() {
        AppUser student = user(7L, UserRole.STUDENT);
        when(examRepository.findStudentAssessments(7L, Set.of(ExamStatus.GENERATED, ExamStatus.SUBMITTED)))
                .thenReturn(List.of());
        StudentPaperAttempt withdrawn = attempt(101L, 11L, StudentPaperAttemptStatus.NOT_STARTED, NOW.minusHours(1));
        StudentPaperAttempt invalidRelease = attempt(102L, 12L, StudentPaperAttemptStatus.SUBMITTED, NOW.minusHours(2));
        invalidRelease.setSubmittedAt(NOW.minusMinutes(30));
        StudentPaperAttempt invalidAttempt = attempt(
                103L, 13L, StudentPaperAttemptStatus.INVALIDATED, NOW.minusHours(3));
        StudentPaperAttempt hiddenSuperseded = attempt(104L, 14L, StudentPaperAttemptStatus.SUBMITTED_LATE,
                NOW.minusHours(4));
        hiddenSuperseded.setSubmittedAt(NOW.minusMinutes(20));
        StudentPaperAttempt visibleSuperseded = attempt(105L, 15L, StudentPaperAttemptStatus.SUBMITTED,
                NOW.minusHours(5));
        visibleSuperseded.setSubmittedAt(NOW.minusMinutes(10));
        StudentPaperAttempt visibleSupersededPending = attempt(
                106L, 16L, StudentPaperAttemptStatus.IN_PROGRESS, NOW.minusHours(6));
        when(attemptRepository.findByStudentIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(
                withdrawn,
                invalidRelease,
                invalidAttempt,
                hiddenSuperseded,
                visibleSuperseded,
                visibleSupersededPending));
        when(releaseRepository.findAllById(Set.of(11L, 12L, 13L, 14L, 15L, 16L))).thenReturn(List.of(
                release(11L, 51L, "已撤回", PaperReleaseStatus.WITHDRAWN, null, null),
                release(12L, 52L, "已作废", PaperReleaseStatus.INVALIDATED, null, null),
                release(13L, 53L, "作答作废", PaperReleaseStatus.OPEN, null, null),
                supersededRelease(14L, false),
                supersededRelease(15L, true),
                supersededRelease(16L, true)));

        assertEquals(List.of(), service.listPending(student));
        List<StudentAssessmentSummaryResponse> history = service.listHistory(student);

        assertEquals(List.of(105L), history.stream()
                .map(StudentAssessmentSummaryResponse::getAssessmentId)
                .toList());
    }

    @Test
    void historyCombinesSubmittedRowsAndSortsBySubmissionTimeDescending() {
        AppUser student = user(7L, UserRole.STUDENT);
        Exam legacy = exam(1L, 31L, ExamStatus.SUBMITTED, NOW.minusDays(2), NOW.minusHours(2));
        legacy.setScore(80);
        legacy.setAnsweredCount(8);
        legacy.setCorrectCount(6);
        when(examRepository.findStudentAssessments(7L, Set.of(ExamStatus.GENERATED, ExamStatus.SUBMITTED)))
                .thenReturn(List.of(legacy));
        when(dictionaryRepository.findAllById(Set.of(31L)))
                .thenReturn(List.of(dictionary(31L, "六级词汇")));

        StudentPaperAttempt submitted = attempt(101L, 11L, StudentPaperAttemptStatus.SUBMITTED, NOW.minusDays(1));
        submitted.setSubmittedAt(NOW.minusHours(1));
        submitted.setAnsweredCount(3);
        submitted.setCorrectCount(2);
        submitted.setEarnedScore(new BigDecimal("12.50"));
        submitted.setTotalScore(new BigDecimal("20.00"));
        submitted.setScorePercentage(new BigDecimal("62.50"));
        StudentPaperAttempt late = attempt(102L, 12L, StudentPaperAttemptStatus.SUBMITTED_LATE, NOW.minusDays(1));
        late.setSubmittedAt(NOW.minusMinutes(30));
        when(attemptRepository.findByStudentIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(submitted, late));
        when(releaseRepository.findAllById(Set.of(11L, 12L))).thenReturn(List.of(
                release(11L, 51L, "单元测验", PaperReleaseStatus.OPEN, NOW.minusDays(1), NOW.minusMinutes(1)),
                release(12L, 52L, "补充测验", PaperReleaseStatus.OPEN, NOW.minusDays(1), NOW.minusHours(1))));

        List<StudentAssessmentSummaryResponse> result = service.listHistory(student);

        assertEquals(List.of(102L, 101L, 1L), result.stream()
                .map(StudentAssessmentSummaryResponse::getAssessmentId)
                .toList());
        assertEquals(StudentAssessmentStatus.SUBMITTED_LATE, result.get(0).getStatus());
        assertEquals(StudentAssessmentStatus.SUBMITTED, result.get(1).getStatus());
        assertEquals(new BigDecimal("12.50"), result.get(1).getEarnedScore());
        assertEquals(new BigDecimal("20.00"), result.get(1).getTotalScore());
        assertEquals(new BigDecimal("62.50"), result.get(1).getScorePercentage());
        assertEquals(StudentAssessmentType.LEGACY_GENERATED_EXAM, result.get(2).getAssessmentType());
        assertEquals(true, result.get(2).getScoreVisible());
        assertEquals(new BigDecimal("80"), result.get(2).getScorePercentage());
        assertNull(result.get(2).getEarnedScore());
        assertNull(result.get(2).getTotalScore());
    }

    @Test
    void paperHistoryUsesTask7ScoreVisibilityAndPendingNeverExposesScores() {
        AppUser student = user(7L, UserRole.STUDENT);
        when(examRepository.findStudentAssessments(7L, Set.of(ExamStatus.GENERATED, ExamStatus.SUBMITTED)))
                .thenReturn(List.of());

        StudentPaperAttempt pending = scoredAttempt(
                101L, 11L, StudentPaperAttemptStatus.IN_PROGRESS, null);
        StudentPaperAttempt held = scoredAttempt(
                102L, 12L, StudentPaperAttemptStatus.SUBMITTED, NOW.minusMinutes(4));
        StudentPaperAttempt releasedHeld = scoredAttempt(
                103L, 13L, StudentPaperAttemptStatus.SUBMITTED, NOW.minusMinutes(3));
        StudentPaperAttempt scoreOnly = scoredAttempt(
                104L, 14L, StudentPaperAttemptStatus.SUBMITTED, NOW.minusMinutes(2));
        StudentPaperAttempt scoreAndAnswers = scoredAttempt(
                105L, 15L, StudentPaperAttemptStatus.SUBMITTED_LATE, NOW.minusMinutes(1));
        when(attemptRepository.findByStudentIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(pending, held, releasedHeld, scoreOnly, scoreAndAnswers));

        PaperRelease heldRelease = release(12L, 52L, "待公布", PaperReleaseStatus.OPEN, null, NOW.plusHours(1));
        heldRelease.setResultVisibility(PaperResultVisibility.HIDDEN_UNTIL_RELEASED);
        PaperRelease releasedHeldRelease = release(
                13L, 53L, "已公布", PaperReleaseStatus.OPEN, null, NOW.plusHours(1));
        releasedHeldRelease.setResultVisibility(PaperResultVisibility.HIDDEN_UNTIL_RELEASED);
        releasedHeldRelease.setResultsReleasedAt(NOW.minusMinutes(10));
        PaperRelease scoreOnlyRelease = release(
                14L, 54L, "仅分数", PaperReleaseStatus.OPEN, null, NOW.plusHours(1));
        scoreOnlyRelease.setResultVisibility(PaperResultVisibility.SCORE_ONLY);
        PaperRelease scoreAndAnswersRelease = release(
                15L, 55L, "分数答案", PaperReleaseStatus.OPEN, null, NOW.minusMinutes(10));
        scoreAndAnswersRelease.setResultVisibility(PaperResultVisibility.SCORE_AND_ANSWERS);
        when(releaseRepository.findAllById(Set.of(11L, 12L, 13L, 14L, 15L))).thenReturn(List.of(
                release(11L, 51L, "进行中", PaperReleaseStatus.OPEN, null, NOW.plusHours(1)),
                heldRelease,
                releasedHeldRelease,
                scoreOnlyRelease,
                scoreAndAnswersRelease));

        StudentAssessmentSummaryResponse pendingResult = service.listPending(student).get(0);
        assertEquals(false, pendingResult.getScoreVisible());
        assertNull(pendingResult.getCorrectCount());
        assertNull(pendingResult.getEarnedScore());
        assertNull(pendingResult.getScorePercentage());

        Map<Long, StudentAssessmentSummaryResponse> history = service.listHistory(student).stream()
                .collect(Collectors.toMap(StudentAssessmentSummaryResponse::getAssessmentId, Function.identity()));
        assertEquals(false, history.get(102L).getScoreVisible());
        assertNull(history.get(102L).getCorrectCount());
        assertNull(history.get(102L).getEarnedScore());
        assertNull(history.get(102L).getScorePercentage());
        for (Long visibleId : List.of(103L, 104L, 105L)) {
            assertEquals(true, history.get(visibleId).getScoreVisible());
            assertEquals(3, history.get(visibleId).getCorrectCount());
            assertEquals(new BigDecimal("12.50"), history.get(visibleId).getEarnedScore());
            assertEquals(new BigDecimal("62.50"), history.get(visibleId).getScorePercentage());
        }
    }

    @Test
    void startAndDeadlineAreInclusiveAndOnlyStrictlyPastDeadlineIsOverdue() {
        AppUser student = user(7L, UserRole.STUDENT);
        when(examRepository.findStudentAssessments(7L, Set.of(ExamStatus.GENERATED, ExamStatus.SUBMITTED)))
                .thenReturn(List.of());
        when(attemptRepository.findByStudentIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(
                attempt(101L, 11L, StudentPaperAttemptStatus.NOT_STARTED, NOW.minusHours(1)),
                attempt(102L, 12L, StudentPaperAttemptStatus.NOT_STARTED, NOW.minusHours(1)),
                attempt(103L, 13L, StudentPaperAttemptStatus.NOT_STARTED, NOW.minusHours(1))));
        when(releaseRepository.findAllById(Set.of(11L, 12L, 13L))).thenReturn(List.of(
                release(11L, 51L, "正好开始", PaperReleaseStatus.SCHEDULED, NOW, NOW.plusHours(1)),
                release(12L, 52L, "正好截止", PaperReleaseStatus.OPEN, NOW.minusHours(1), NOW),
                release(13L, 53L, "刚刚超时", PaperReleaseStatus.OPEN,
                        NOW.minusHours(1), NOW.minusNanos(1))));

        Map<Long, StudentAssessmentStatus> statuses = service.listPending(student).stream()
                .collect(Collectors.toMap(
                        StudentAssessmentSummaryResponse::getAssessmentId,
                        StudentAssessmentSummaryResponse::getStatus));

        assertEquals(StudentAssessmentStatus.NOT_STARTED, statuses.get(101L));
        assertEquals(StudentAssessmentStatus.NOT_STARTED, statuses.get(102L));
        assertEquals(StudentAssessmentStatus.OVERDUE, statuses.get(103L));
    }

    @Test
    void nonStudentIsRejectedBeforeAssessmentRepositoriesAreQueried() {
        AppUser teacher = user(7L, UserRole.TEACHER);

        assertThrows(AccessDeniedException.class, () -> service.listPending(teacher));
        assertThrows(AccessDeniedException.class, () -> service.listHistory(teacher));

        verify(examRepository, never()).findStudentAssessments(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anySet());
        verify(attemptRepository, never()).findByStudentIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.anyLong());
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private Dictionary dictionary(Long id, String name) {
        Dictionary dictionary = new Dictionary();
        dictionary.setId(id);
        dictionary.setName(name);
        return dictionary;
    }

    private Exam exam(
            Long id,
            Long dictionaryId,
            ExamStatus status,
            LocalDateTime createdAt,
            LocalDateTime submittedAt) {
        Exam exam = new Exam();
        exam.setId(id);
        exam.setDictionaryId(dictionaryId);
        exam.setQuestionCount(10);
        exam.setAnsweredCount(0);
        exam.setCorrectCount(0);
        exam.setScore(0);
        exam.setCreatedByUserId(7L);
        exam.setTargetUserId(7L);
        exam.setStatus(status);
        exam.setAssignedAt(createdAt);
        exam.setCreatedAt(createdAt);
        exam.setSubmittedAt(submittedAt);
        return exam;
    }

    private StudentPaperAttempt attempt(
            Long id,
            Long releaseId,
            StudentPaperAttemptStatus status,
            LocalDateTime createdAt) {
        StudentPaperAttempt attempt = new StudentPaperAttempt();
        attempt.setId(id);
        attempt.setPaperReleaseId(releaseId);
        attempt.setStudentId(7L);
        attempt.setStatus(status);
        attempt.setAnsweredCount(0);
        attempt.setCorrectCount(0);
        attempt.setEarnedScore(BigDecimal.ZERO);
        attempt.setTotalScore(new BigDecimal("20.00"));
        attempt.setCreatedAt(createdAt);
        return attempt;
    }

    private StudentPaperAttempt scoredAttempt(
            Long id,
            Long releaseId,
            StudentPaperAttemptStatus status,
            LocalDateTime submittedAt) {
        StudentPaperAttempt attempt = attempt(id, releaseId, status, NOW.minusHours(1));
        attempt.setAnsweredCount(4);
        attempt.setCorrectCount(3);
        attempt.setEarnedScore(new BigDecimal("12.50"));
        attempt.setScorePercentage(new BigDecimal("62.50"));
        attempt.setSubmittedAt(submittedAt);
        return attempt;
    }

    private PaperRelease release(
            Long id,
            Long templateId,
            String title,
            PaperReleaseStatus status,
            LocalDateTime startTime,
            LocalDateTime deadline) {
        PaperRelease release = new PaperRelease();
        release.setId(id);
        release.setPaperTemplateId(templateId);
        release.setTitle(title);
        release.setStatus(status);
        release.setQuestionCount(4);
        release.setTotalScore(new BigDecimal("20.00"));
        release.setStartTime(startTime);
        release.setDeadline(deadline);
        release.setResultVisibility(PaperResultVisibility.SCORE_ONLY);
        release.setShowSupersededToStudents(false);
        release.setCreatedAt(NOW.minusDays(1));
        return release;
    }

    private PaperRelease supersededRelease(Long id, boolean visible) {
        PaperRelease release = release(
                id,
                50L + id,
                visible ? "可见旧试卷" : "隐藏旧试卷",
                PaperReleaseStatus.SUPERSEDED,
                NOW.minusDays(1),
                NOW.minusHours(1));
        release.setShowSupersededToStudents(visible);
        return release;
    }
}
