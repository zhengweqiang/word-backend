package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.SubmitStudentPaperRequest;
import com.example.words.model.AppUser;
import com.example.words.model.PaperBlankAnswerPolicy;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperReleaseQuestion;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.PaperResultVisibility;
import com.example.words.model.PointSourceType;
import com.example.words.model.QuestionType;
import com.example.words.model.StudentPaperAttempt;
import com.example.words.model.StudentPaperAttemptStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.PaperReleaseQuestionRepository;
import com.example.words.repository.PaperReleaseRepository;
import com.example.words.repository.StudentPaperAnswerRepository;
import com.example.words.repository.StudentPaperAttemptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentPaperAttemptPointEventTest {

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
        student = new AppUser();
        student.setId(20L);
        student.setRole(UserRole.STUDENT);
    }

    @Test
    void onTimeSubmissionPublishesExamPointEventAfterCommit() {
        StudentPaperAttempt attempt = attempt();
        PaperRelease release = release(NOW);
        PaperReleaseQuestion question = question();
        when(attemptRepository.findPaperReleaseIdByIdAndStudentId(100L, 20L)).thenReturn(Optional.of(10L));
        when(releaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(release));
        when(attemptRepository.findByIdAndStudentIdForUpdate(100L, 20L)).thenReturn(Optional.of(attempt));
        when(releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(10L))
                .thenReturn(List.of(question));
        when(answerRepository.findByAttemptId(100L)).thenReturn(List.of());
        when(attemptRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(100L, new SubmitStudentPaperRequest(3L, List.of()), student);

        verify(studentPointEventPublisher).publishAfterCommit(argThat(request ->
                Long.valueOf(20L).equals(request.studentId())
                        && Long.valueOf(100L).equals(request.sourceId())
                        && "paper-release-attempt:100:SUBMITTED".equals(request.sourceKey())
                        && "EXAM".equals(request.ruleCode())));
    }

    @Test
    void lateSubmissionDoesNotPublishExamPointEvent() {
        StudentPaperAttempt attempt = attempt();
        PaperRelease release = release(NOW.minusNanos(1));
        PaperReleaseQuestion question = question();
        when(attemptRepository.findPaperReleaseIdByIdAndStudentId(100L, 20L)).thenReturn(Optional.of(10L));
        when(releaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(release));
        when(attemptRepository.findByIdAndStudentIdForUpdate(100L, 20L)).thenReturn(Optional.of(attempt));
        when(releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(10L))
                .thenReturn(List.of(question));
        when(answerRepository.findByAttemptId(100L)).thenReturn(List.of());
        when(attemptRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(100L, new SubmitStudentPaperRequest(3L, List.of()), student);

        verify(studentPointEventPublisher, never()).publishAfterCommit(any());
    }

    @ParameterizedTest
    @MethodSource("submissionCases")
    void finalSubmissionPersistsTimingStatusAndBuildsStableFuturePointIdentity(
            LocalDateTime deadline,
            StudentPaperAttemptStatus expectedStatus,
            String expectedSourceKey
    ) throws Exception {
        StudentPaperAttempt attempt = attempt();
        PaperRelease release = release(deadline);
        PaperReleaseQuestion question = question();
        when(attemptRepository.findPaperReleaseIdByIdAndStudentId(100L, 20L)).thenReturn(Optional.of(10L));
        when(releaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(release));
        when(attemptRepository.findByIdAndStudentIdForUpdate(100L, 20L)).thenReturn(Optional.of(attempt));
        when(releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(10L))
                .thenReturn(List.of(question));
        when(answerRepository.findByAttemptId(100L)).thenReturn(List.of());
        when(attemptRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(100L, new SubmitStudentPaperRequest(3L, List.of()), student);

        assertEquals(expectedStatus, attempt.getStatus());
        assertEquals(NOW, attempt.getSubmittedAt());
        whenPersistedStatusIs(expectedStatus);

        StudentPaperAttemptService.PaperAttemptPointSourceIdentity identity =
                StudentPaperAttemptService.pointSourceIdentity(attempt.getId(), attempt.getStatus());
        assertEquals(PointSourceType.PAPER_RELEASE_ATTEMPT, identity.sourceType());
        assertEquals(100L, identity.sourceId());
        assertEquals(expectedSourceKey, identity.sourceKey());
    }

    private void whenPersistedStatusIs(StudentPaperAttemptStatus expectedStatus) {
        org.mockito.Mockito.verify(attemptRepository).saveAndFlush(argThat(saved ->
                expectedStatus == saved.getStatus() && NOW.equals(saved.getSubmittedAt())));
    }

    private StudentPaperAttempt attempt() {
        StudentPaperAttempt attempt = new StudentPaperAttempt();
        attempt.setId(100L);
        attempt.setPaperReleaseId(10L);
        attempt.setStudentId(20L);
        attempt.setStatus(StudentPaperAttemptStatus.IN_PROGRESS);
        attempt.setVersion(3L);
        attempt.setAnsweredCount(0);
        attempt.setCorrectCount(0);
        attempt.setEarnedScore(BigDecimal.ZERO);
        attempt.setTotalScore(new BigDecimal("5.00"));
        return attempt;
    }

    private PaperRelease release(LocalDateTime deadline) {
        PaperRelease release = new PaperRelease();
        release.setId(10L);
        release.setPaperTemplateId(1L);
        release.setTitle("Vocabulary check");
        release.setStatus(PaperReleaseStatus.OPEN);
        release.setQuestionCount(1);
        release.setTotalScore(new BigDecimal("5.00"));
        release.setStartTime(NOW.minusHours(1));
        release.setDeadline(deadline);
        release.setBlankAnswerPolicy(PaperBlankAnswerPolicy.ALLOW_BLANK);
        release.setResultVisibility(PaperResultVisibility.SCORE_ONLY);
        return release;
    }

    private PaperReleaseQuestion question() {
        PaperReleaseQuestion question = new PaperReleaseQuestion();
        question.setId(1000L);
        question.setPaperReleaseId(10L);
        question.setQuestionOrder(1);
        question.setQuestionType(QuestionType.SINGLE_CHOICE);
        question.setStem("Pick one");
        question.setOptionsJson("{\"A\":\"Alpha\"}");
        question.setAcceptedAnswersJson("[\"A\"]");
        question.setScore(new BigDecimal("5.00"));
        return question;
    }

    private static Stream<Arguments> submissionCases() {
        return Stream.of(
                Arguments.of(NOW, StudentPaperAttemptStatus.SUBMITTED,
                        "paper-release-attempt:100:SUBMITTED"),
                Arguments.of(NOW.minusNanos(1), StudentPaperAttemptStatus.SUBMITTED_LATE,
                        "paper-release-attempt:100:SUBMITTED_LATE")
        );
    }
}
