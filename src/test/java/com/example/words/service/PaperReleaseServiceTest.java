package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.words.dto.InvalidatePaperReleaseRequest;
import com.example.words.dto.PaperReleaseResponse;
import com.example.words.dto.PublishPaperRequest;
import com.example.words.dto.SupersedePaperReleaseRequest;
import com.example.words.dto.WithdrawPaperReleaseRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.ClassroomMember;
import com.example.words.model.PaperBlankAnswerPolicy;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperReleaseQuestion;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.PaperResultVisibility;
import com.example.words.model.PaperTemplate;
import com.example.words.model.PaperTemplateQuestion;
import com.example.words.model.PaperTemplateStatus;
import com.example.words.model.QuestionType;
import com.example.words.model.StudentPaperAttempt;
import com.example.words.model.StudentPaperAttemptStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.PaperReleaseQuestionRepository;
import com.example.words.repository.PaperReleaseRepository;
import com.example.words.repository.PaperReleaseTargetRepository;
import com.example.words.repository.StudentPaperAttemptRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperReleaseServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 10, 0);

    @Mock
    private PaperReleaseRepository releaseRepository;
    @Mock
    private PaperReleaseQuestionRepository releaseQuestionRepository;
    @Mock
    private PaperReleaseTargetRepository targetRepository;
    @Mock
    private StudentPaperAttemptRepository attemptRepository;
    @Mock
    private PaperTemplateService paperTemplateService;
    @Mock
    private ExamPaperAccessService accessService;
    @Mock
    private ClassroomMemberRepository classroomMemberRepository;
    @Mock
    private AppUserRepository userRepository;

    private PaperReleaseService service;
    private long nextReleaseId;
    private long nextAttemptId;

    @BeforeEach
    void setUp() {
        nextReleaseId = 300L;
        nextAttemptId = 500L;
        service = new PaperReleaseService(
                releaseRepository,
                releaseQuestionRepository,
                targetRepository,
                attemptRepository,
                paperTemplateService,
                accessService,
                classroomMemberRepository,
                userRepository,
                new ExamPaperSnapshotService(),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-29T02:00:00Z"), ZoneOffset.ofHours(8)));
        lenient().when(releaseRepository.saveAndFlush(any(PaperRelease.class))).thenAnswer(invocation -> {
            PaperRelease release = invocation.getArgument(0);
            if (release.getId() == null) {
                release.setId(nextReleaseId++);
                release.setCreatedAt(NOW);
            }
            return release;
        });
        lenient().when(releaseQuestionRepository.saveAllAndFlush(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(targetRepository.saveAllAndFlush(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(attemptRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<StudentPaperAttempt> attempts = invocation.getArgument(0);
            attempts.forEach(attempt -> {
                if (attempt.getId() == null) {
                    attempt.setId(nextAttemptId++);
                }
            });
            return attempts;
        });
    }

    @Test
    void publishesFrozenSnapshotToExplicitStudentsWithDefaults() {
        stubPublicationSource();
        stubStudents(11L, 12L);

        PaperReleaseResponse response = service.publish(
                new PublishPaperRequest(10L, List.of(11L, 12L), List.of(), null, null, null, null),
                teacher(7L));

        assertEquals(PaperReleaseStatus.OPEN, response.getStatus());
        assertEquals(NOW, response.getStartTime());
        assertEquals(PaperBlankAnswerPolicy.ALLOW_BLANK, response.getBlankAnswerPolicy());
        assertEquals(PaperResultVisibility.SCORE_ONLY, response.getResultVisibility());
        assertEquals(new BigDecimal("3.00"), response.getTotalScore());
        assertEquals(2, response.getTargets().size());
        assertTrue(response.getTargets().stream()
                .allMatch(target -> target.getAttemptStatus() == StudentPaperAttemptStatus.NOT_STARTED));
        verify(accessService).ensureCanPublishToStudent(teacher(7L), 11L);
        verify(accessService).ensureCanPublishToStudent(teacher(7L), 12L);
    }

    @Test
    void expandsClassroomsDeduplicatesStudentsAndPreservesEveryClassroomTrace() {
        stubPublicationSource();
        stubStudents(11L, 12L, 13L);
        when(classroomMemberRepository.findByClassroomId(31L)).thenReturn(List.of(member(31L, 11L), member(31L, 12L)));
        when(classroomMemberRepository.findByClassroomId(32L)).thenReturn(List.of(member(32L, 11L), member(32L, 13L)));

        PaperReleaseResponse response = service.publish(
                new PublishPaperRequest(10L, List.of(11L), List.of(31L, 32L), null, null, null, null),
                teacher(7L));

        assertEquals(List.of(11L, 12L, 13L), response.getTargets().stream()
                .map(target -> target.getStudentId()).toList());
        assertEquals(List.of(31L, 32L), response.getTargets().get(0).getSourceClassroomIds());
        assertEquals(List.of(31L), response.getTargets().get(1).getSourceClassroomIds());
        assertEquals(List.of(32L), response.getTargets().get(2).getSourceClassroomIds());
        verify(accessService).ensureCanPublishToClassroom(teacher(7L), 31L);
        verify(accessService).ensureCanPublishToClassroom(teacher(7L), 32L);
    }

    @Test
    void validatesEveryTargetBeforeWritingReleaseData() {
        stubPublicationSource();
        when(userRepository.findAllById(List.of(11L, 99L))).thenReturn(List.of(student(11L)));

        assertThrows(BadRequestException.class, () -> service.publish(
                new PublishPaperRequest(10L, List.of(11L, 99L), List.of(), null, null, null, null),
                teacher(7L)));

        verify(releaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUnauthorizedStudentAndArchivedClassroomBeforeWriting() {
        stubPublicationSource();
        doThrow(new org.springframework.security.access.AccessDeniedException("student denied"))
                .when(accessService).ensureCanPublishToStudent(teacher(7L), 11L);

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.publish(
                new PublishPaperRequest(10L, List.of(11L), List.of(), null, null, null, null),
                teacher(7L)));

        doThrow(new org.springframework.security.access.AccessDeniedException("archived classroom"))
                .when(accessService).ensureCanPublishToClassroom(teacher(7L), 31L);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.publish(
                new PublishPaperRequest(10L, List.of(), List.of(31L), null, null, null, null),
                teacher(7L)));
        verify(releaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void laterClassMembershipChangesDoNotChangeFrozenTargets() {
        stubPublicationSource();
        stubStudents(11L, 12L);
        List<ClassroomMember> currentMembers = new ArrayList<>(List.of(member(31L, 11L), member(31L, 12L)));
        when(classroomMemberRepository.findByClassroomId(31L)).thenReturn(currentMembers);

        PaperReleaseResponse response = service.publish(
                new PublishPaperRequest(10L, List.of(), List.of(31L), null, null, null, null),
                teacher(7L));
        currentMembers.clear();
        currentMembers.add(member(31L, 13L));

        assertEquals(List.of(11L, 12L), response.getTargets().stream()
                .map(target -> target.getStudentId()).toList());
    }

    @Test
    void futureStartSchedulesReleaseAndRejectsDeadlineBeforeStart() {
        stubPublicationSource();
        stubStudents(11L);
        LocalDateTime future = NOW.plusDays(1);

        PaperReleaseResponse response = service.publish(new PublishPaperRequest(
                10L, List.of(11L), List.of(), future, future.plusHours(2),
                PaperBlankAnswerPolicy.REQUIRE_ALL_ANSWERED, PaperResultVisibility.HIDDEN_UNTIL_RELEASED), teacher(7L));

        assertEquals(PaperReleaseStatus.SCHEDULED, response.getStatus());
        assertEquals(PaperBlankAnswerPolicy.REQUIRE_ALL_ANSWERED, response.getBlankAnswerPolicy());
        assertThrows(BadRequestException.class, () -> service.publish(new PublishPaperRequest(
                10L, List.of(11L), List.of(), future, future.minusMinutes(1), null, null), teacher(7L)));
    }

    @Test
    void releaseSnapshotDoesNotChangeWhenTemplateIsEditedLater() {
        PaperTemplateService.PublicationSource source = publicationSource();
        when(paperTemplateService.lockReadyForPublishing(10L, teacher(7L))).thenReturn(source);
        stubStudents(11L);

        service.publish(new PublishPaperRequest(10L, List.of(11L), List.of(), null, null, null, null), teacher(7L));
        source.questions().get(0).setStem("Edited later");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<PaperReleaseQuestion>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(releaseQuestionRepository).saveAllAndFlush(captor.capture());
        PaperReleaseQuestion frozenFirst = captor.getValue().stream()
                .filter(snapshot -> Long.valueOf(101L).equals(snapshot.getPaperTemplateQuestionId()))
                .findFirst().orElseThrow();
        assertEquals("Frozen one", frozenFirst.getStem());
    }

    @Test
    void freezesOneShuffledQuestionAndOptionPresentationForEveryTarget() throws Exception {
        PaperTemplateService.PublicationSource source = shuffledPublicationSource();
        when(paperTemplateService.lockReadyForPublishing(10L, teacher(7L))).thenReturn(source);
        stubStudents(11L, 12L);

        PaperReleaseResponse response = service.publish(
                new PublishPaperRequest(10L, List.of(11L, 12L), List.of(), null, null, null, null),
                teacher(7L));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<PaperReleaseQuestion>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(releaseQuestionRepository).saveAllAndFlush(captor.capture());
        List<PaperReleaseQuestion> snapshots = captor.getValue().stream()
                .sorted(java.util.Comparator.comparing(PaperReleaseQuestion::getQuestionOrder))
                .toList();

        assertEquals(List.of(1, 2, 3), snapshots.stream()
                .map(PaperReleaseQuestion::getQuestionOrder).toList());
        assertNotEquals(List.of(101L, 102L, 103L), snapshots.stream()
                .map(PaperReleaseQuestion::getPaperTemplateQuestionId).toList());
        assertEquals(3, snapshots.size());
        assertEquals(2, response.getTargets().size());
        assertTrue(snapshots.stream().allMatch(snapshot -> response.getId().equals(snapshot.getPaperReleaseId())));

        PaperReleaseQuestion shuffledChoice = snapshots.stream()
                .filter(snapshot -> Long.valueOf(101L).equals(snapshot.getPaperTemplateQuestionId()))
                .findFirst().orElseThrow();
        Map<String, String> originalOptions = Map.of(
                "A", "Alpha", "B", "Beta", "C", "Gamma", "D", "Delta");
        Map<String, String> shuffledOptions = new ObjectMapper().readValue(
                shuffledChoice.getOptionsJson(), new TypeReference<LinkedHashMap<String, String>>() {
                });
        List<String> remappedAnswers = new ObjectMapper().readValue(
                shuffledChoice.getAcceptedAnswersJson(), new TypeReference<List<String>>() {
                });
        assertEquals(Set.of("A", "B", "C", "D"), shuffledOptions.keySet());
        assertEquals(Set.copyOf(originalOptions.values()), Set.copyOf(shuffledOptions.values()));
        assertNotEquals(originalOptions, shuffledOptions);
        assertEquals(shuffledOptions.entrySet().stream()
                        .filter(entry -> Set.of("Beta", "Delta").contains(entry.getValue()))
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList(),
                remappedAnswers);

        PaperReleaseQuestion fillBlank = snapshots.stream()
                .filter(snapshot -> Long.valueOf(103L).equals(snapshot.getPaperTemplateQuestionId()))
                .findFirst().orElseThrow();
        assertEquals("{}", fillBlank.getOptionsJson());
        assertEquals("[\"answer\"]", fillBlank.getAcceptedAnswersJson());

        source.questions().get(0).setOptionsJson("{\"A\":\"Changed later\"}");
        source.questions().get(0).setAcceptedAnswersJson("[\"A\"]");
        assertNotEquals("{\"A\":\"Changed later\"}", shuffledChoice.getOptionsJson());
        assertNotEquals("[\"A\"]", shuffledChoice.getAcceptedAnswersJson());
    }

    @Test
    void sameSavedReleaseIdProducesSameFrozenPresentation() {
        PaperTemplateService.PublicationSource source = shuffledPublicationSource();
        when(paperTemplateService.lockReadyForPublishing(10L, teacher(7L))).thenReturn(source);
        stubStudents(11L);
        PublishPaperRequest request =
                new PublishPaperRequest(10L, List.of(11L), List.of(), null, null, null, null);

        service.publish(request, teacher(7L));
        nextReleaseId = 300L;
        service.publish(request, teacher(7L));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<PaperReleaseQuestion>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(releaseQuestionRepository, times(2)).saveAllAndFlush(captor.capture());
        List<List<String>> presentations = captor.getAllValues().stream()
                .map(snapshots -> snapshots.stream()
                        .sorted(java.util.Comparator.comparing(PaperReleaseQuestion::getQuestionOrder))
                        .map(snapshot -> snapshot.getPaperTemplateQuestionId()
                                + ":" + snapshot.getOptionsJson()
                                + ":" + snapshot.getAcceptedAnswersJson())
                        .toList())
                .toList();
        assertEquals(presentations.get(0), presentations.get(1));
    }

    @Test
    void withdrawsBeforeFinalSubmissionButRejectsAfterSubmission() {
        PaperRelease release = release(200L, PaperReleaseStatus.OPEN);
        when(releaseRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(release));
        when(attemptRepository.findByPaperReleaseIdForUpdate(200L))
                .thenReturn(List.of(attempt(11L, StudentPaperAttemptStatus.IN_PROGRESS)));

        PaperReleaseResponse response = service.withdraw(
                200L, new WithdrawPaperReleaseRequest("Published by mistake"), teacher(7L));

        assertEquals(PaperReleaseStatus.WITHDRAWN, response.getStatus());
        assertEquals("Published by mistake", response.getWithdrawReason());

        PaperRelease submittedRelease = release(201L, PaperReleaseStatus.OPEN);
        when(releaseRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(submittedRelease));
        when(attemptRepository.findByPaperReleaseIdForUpdate(201L))
                .thenReturn(List.of(attempt(11L, StudentPaperAttemptStatus.SUBMITTED_LATE)));
        assertThrows(BadRequestException.class, () -> service.withdraw(
                201L, new WithdrawPaperReleaseRequest("Too late"), teacher(7L)));
    }

    @Test
    void invalidationPreservesFinalStatusesAndScoresButInvalidatesNonFinalAttempts() {
        PaperRelease release = release(200L, PaperReleaseStatus.OPEN);
        StudentPaperAttempt submitted = attempt(11L, StudentPaperAttemptStatus.SUBMITTED);
        submitted.setEarnedScore(new BigDecimal("2.00"));
        StudentPaperAttempt submittedLate = attempt(12L, StudentPaperAttemptStatus.SUBMITTED_LATE);
        submittedLate.setEarnedScore(new BigDecimal("1.00"));
        StudentPaperAttempt inProgress = attempt(13L, StudentPaperAttemptStatus.IN_PROGRESS);
        when(releaseRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(release));
        when(attemptRepository.findByPaperReleaseIdForUpdate(200L))
                .thenReturn(List.of(submitted, submittedLate, inProgress));

        PaperReleaseResponse response = service.invalidate(
                200L, new InvalidatePaperReleaseRequest("Incorrect answer key"), teacher(7L));

        assertEquals(PaperReleaseStatus.INVALIDATED, response.getStatus());
        assertEquals(StudentPaperAttemptStatus.SUBMITTED, submitted.getStatus());
        assertEquals(StudentPaperAttemptStatus.SUBMITTED_LATE, submittedLate.getStatus());
        assertEquals(StudentPaperAttemptStatus.INVALIDATED, inProgress.getStatus());
        assertEquals(new BigDecimal("2.00"), submitted.getEarnedScore());
        assertEquals(new BigDecimal("1.00"), submittedLate.getEarnedScore());
        assertEquals("Incorrect answer key", submitted.getInvalidateReason());
        assertEquals("Incorrect answer key", submittedLate.getInvalidateReason());
        assertThrows(BadRequestException.class, () -> service.invalidate(
                200L, new InvalidatePaperReleaseRequest(" "), teacher(7L)));
    }

    @Test
    void supersedeUsesCurrentPaperSnapshotAndCompleteFrozenTargetList() {
        PaperRelease original = release(200L, PaperReleaseStatus.OPEN);
        when(releaseRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(original));
        when(targetRepository.findByPaperReleaseId(200L)).thenReturn(List.of(
                target(200L, 11L, List.of(31L)), target(200L, 12L, List.of(31L, 32L))));
        StudentPaperAttempt submitted = attempt(11L, StudentPaperAttemptStatus.SUBMITTED);
        stubPublicationSource();

        PaperReleaseResponse replacement = service.supersede(200L, new SupersedePaperReleaseRequest(
                "Corrected question", null, null, null, null, true), teacher(7L));

        assertEquals(PaperReleaseStatus.SUPERSEDED, original.getStatus());
        assertTrue(original.getShowSupersededToStudents());
        assertEquals(replacement.getId(), original.getSupersededByReleaseId());
        assertEquals(original.getId(), replacement.getSupersedesReleaseId());
        assertEquals(List.of(11L, 12L), replacement.getTargets().stream()
                .map(target -> target.getStudentId()).toList());
        assertEquals(StudentPaperAttemptStatus.SUBMITTED, submitted.getStatus());
        assertNotEquals(original.getId(), replacement.getId());
    }

    @Test
    void terminalCorrectionTransitionsCannotBeRepeatedOrRewritten() {
        PaperRelease release = release(200L, PaperReleaseStatus.WITHDRAWN);
        when(releaseRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(release));

        assertThrows(BadRequestException.class, () -> service.withdraw(
                200L, new WithdrawPaperReleaseRequest("Again"), teacher(7L)));
        assertThrows(BadRequestException.class, () -> service.invalidate(
                200L, new InvalidatePaperReleaseRequest("Rewrite"), teacher(7L)));
    }

    @Test
    void onlyPublisherOrAdministratorCanCorrectARelease() {
        PaperRelease deniedRelease = release(200L, PaperReleaseStatus.OPEN);
        when(releaseRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(deniedRelease));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.withdraw(
                200L, new WithdrawPaperReleaseRequest("Not mine"), teacher(8L)));

        PaperRelease adminRelease = release(201L, PaperReleaseStatus.OPEN);
        when(releaseRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(adminRelease));
        when(attemptRepository.findByPaperReleaseIdForUpdate(201L)).thenReturn(List.of());
        AppUser admin = user(1L, UserRole.ADMIN);
        PaperReleaseResponse response = service.withdraw(
                201L, new WithdrawPaperReleaseRequest("Administrative correction"), admin);

        assertEquals(PaperReleaseStatus.WITHDRAWN, response.getStatus());
        assertEquals(1L, response.getWithdrawnByUserId());
    }

    @Test
    void everyCorrectionLocksReleaseBeforeItsAttempts() {
        PaperRelease withdrawRelease = release(200L, PaperReleaseStatus.OPEN);
        when(releaseRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(withdrawRelease));
        when(attemptRepository.findByPaperReleaseIdForUpdate(200L)).thenReturn(List.of());
        service.withdraw(200L, new WithdrawPaperReleaseRequest("Withdraw"), teacher(7L));
        InOrder withdrawOrder = inOrder(releaseRepository, attemptRepository);
        withdrawOrder.verify(releaseRepository).findByIdForUpdate(200L);
        withdrawOrder.verify(attemptRepository).findByPaperReleaseIdForUpdate(200L);

        PaperRelease invalidateRelease = release(201L, PaperReleaseStatus.OPEN);
        when(releaseRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(invalidateRelease));
        when(attemptRepository.findByPaperReleaseIdForUpdate(201L)).thenReturn(List.of());
        service.invalidate(201L, new InvalidatePaperReleaseRequest("Invalidate"), teacher(7L));
        InOrder invalidateOrder = inOrder(releaseRepository, attemptRepository);
        invalidateOrder.verify(releaseRepository).findByIdForUpdate(201L);
        invalidateOrder.verify(attemptRepository).findByPaperReleaseIdForUpdate(201L);

        PaperRelease supersedeRelease = release(202L, PaperReleaseStatus.OPEN);
        when(releaseRepository.findByIdForUpdate(202L)).thenReturn(Optional.of(supersedeRelease));
        when(attemptRepository.findByPaperReleaseIdForUpdate(202L)).thenReturn(List.of());
        when(targetRepository.findByPaperReleaseId(202L))
                .thenReturn(List.of(target(202L, 11L, List.of(31L))));
        stubPublicationSource();
        service.supersede(202L, new SupersedePaperReleaseRequest(
                "Supersede", null, null, null, null, false), teacher(7L));
        InOrder supersedeOrder = inOrder(releaseRepository, attemptRepository);
        supersedeOrder.verify(releaseRepository).findByIdForUpdate(202L);
        supersedeOrder.verify(attemptRepository).findByPaperReleaseIdForUpdate(202L);
    }

    private void stubPublicationSource() {
        when(paperTemplateService.lockReadyForPublishing(10L, teacher(7L))).thenReturn(publicationSource());
    }

    private PaperTemplateService.PublicationSource publicationSource() {
        PaperTemplate paper = new PaperTemplate();
        paper.setId(10L);
        paper.setTitle("Vocabulary quiz");
        paper.setInstructions("Answer carefully");
        paper.setOwnerUserId(7L);
        paper.setStatus(PaperTemplateStatus.READY);
        paper.setShuffleQuestions(true);
        paper.setShuffleOptions(false);
        paper.setTotalScore(new BigDecimal("3.00"));
        return new PaperTemplateService.PublicationSource(paper, new ArrayList<>(List.of(
                question(101L, 1, "Frozen one", "1.00"),
                question(102L, 2, "Frozen two", "2.00"))));
    }

    private PaperTemplateService.PublicationSource shuffledPublicationSource() {
        PaperTemplate paper = new PaperTemplate();
        paper.setId(10L);
        paper.setTitle("Shuffled quiz");
        paper.setOwnerUserId(7L);
        paper.setStatus(PaperTemplateStatus.READY);
        paper.setShuffleQuestions(true);
        paper.setShuffleOptions(true);
        paper.setTotalScore(new BigDecimal("3.00"));
        return new PaperTemplateService.PublicationSource(paper, new ArrayList<>(List.of(
                choiceQuestion(101L, 1, QuestionType.MULTIPLE_CHOICE,
                        Map.of("A", "Alpha", "B", "Beta", "C", "Gamma", "D", "Delta"),
                        List.of("B", "D")),
                choiceQuestion(102L, 2, QuestionType.SINGLE_CHOICE,
                        Map.of("A", "One", "B", "Two", "C", "Three"), List.of("A")),
                fillBlankQuestion(103L, 3))));
    }

    private PaperTemplateQuestion fillBlankQuestion(Long id, int order) {
        PaperTemplateQuestion question = question(id, order, "Fill blank", "1.00");
        question.setOptionsJson("{}");
        return question;
    }

    private PaperTemplateQuestion choiceQuestion(
            Long id,
            int order,
            QuestionType type,
            Map<String, String> options,
            List<String> acceptedAnswers) {
        PaperTemplateQuestion question = question(id, order, "Choice " + id, "1.00");
        question.setQuestionType(type);
        try {
            question.setOptionsJson(new ObjectMapper().writeValueAsString(new java.util.TreeMap<>(options)));
            question.setAcceptedAnswersJson(new ObjectMapper().writeValueAsString(acceptedAnswers));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        return question;
    }

    private PaperTemplateQuestion question(Long id, int order, String stem, String score) {
        PaperTemplateQuestion question = new PaperTemplateQuestion();
        question.setId(id);
        question.setPaperTemplateId(10L);
        question.setQuestionOrder(order);
        question.setQuestionType(QuestionType.FILL_IN_BLANK);
        question.setStem(stem);
        question.setAcceptedAnswersJson("[\"answer\"]");
        question.setScore(new BigDecimal(score));
        return question;
    }

    private void stubStudents(Long... ids) {
        List<Long> requested = List.of(ids);
        when(userRepository.findAllById(requested)).thenReturn(requested.stream().map(this::student).toList());
    }

    private AppUser student(Long id) {
        AppUser student = new AppUser();
        student.setId(id);
        student.setRole(UserRole.STUDENT);
        return student;
    }

    private AppUser teacher(Long id) {
        return user(id, UserRole.TEACHER);
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private ClassroomMember member(Long classroomId, Long studentId) {
        ClassroomMember member = new ClassroomMember();
        member.setClassroomId(classroomId);
        member.setStudentId(studentId);
        return member;
    }

    private PaperRelease release(Long id, PaperReleaseStatus status) {
        PaperRelease release = new PaperRelease();
        release.setId(id);
        release.setPaperTemplateId(10L);
        release.setPublishedByUserId(7L);
        release.setTitle("Vocabulary quiz");
        release.setStatus(status);
        release.setQuestionCount(2);
        release.setTotalScore(new BigDecimal("3.00"));
        release.setShuffleQuestions(false);
        release.setShuffleOptions(false);
        release.setStartTime(NOW);
        release.setBlankAnswerPolicy(PaperBlankAnswerPolicy.ALLOW_BLANK);
        release.setResultVisibility(PaperResultVisibility.SCORE_ONLY);
        release.setShowSupersededToStudents(false);
        return release;
    }

    private StudentPaperAttempt attempt(Long studentId, StudentPaperAttemptStatus status) {
        StudentPaperAttempt attempt = new StudentPaperAttempt();
        attempt.setPaperReleaseId(200L);
        attempt.setStudentId(studentId);
        attempt.setStatus(status);
        attempt.setAnsweredCount(0);
        attempt.setCorrectCount(0);
        attempt.setEarnedScore(BigDecimal.ZERO);
        attempt.setTotalScore(new BigDecimal("3.00"));
        return attempt;
    }

    private com.example.words.model.PaperReleaseTarget target(
            Long releaseId, Long studentId, List<Long> classroomIds) {
        com.example.words.model.PaperReleaseTarget target = new com.example.words.model.PaperReleaseTarget();
        target.setPaperReleaseId(releaseId);
        target.setStudentId(studentId);
        target.setSourceClassroomIdsJson(toJson(classroomIds));
        target.setSourceClassroomId(classroomIds.isEmpty() ? null : classroomIds.get(0));
        return target;
    }

    private String toJson(List<Long> ids) {
        try {
            return new ObjectMapper().writeValueAsString(ids);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
