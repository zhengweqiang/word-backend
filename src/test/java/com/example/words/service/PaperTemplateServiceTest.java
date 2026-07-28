package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.example.words.dto.AddPaperQuestionRequest;
import com.example.words.dto.CopyPaperTemplateRequest;
import com.example.words.dto.CreatePaperTemplateRequest;
import com.example.words.dto.PaperTemplateResponse;
import com.example.words.dto.ReorderPaperQuestionsRequest;
import com.example.words.dto.UpdatePaperQuestionScoreRequest;
import com.example.words.dto.UpdatePaperTemplateRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.PaperTemplate;
import com.example.words.model.PaperTemplateQuestion;
import com.example.words.model.PaperTemplateStatus;
import com.example.words.model.QuestionBankItem;
import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.QuestionType;
import com.example.words.model.UserRole;
import com.example.words.repository.PaperTemplateQuestionRepository;
import com.example.words.repository.PaperTemplateRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperTemplateServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 9, 30);

    @Mock
    private PaperTemplateRepository paperRepository;

    @Mock
    private PaperTemplateQuestionRepository paperQuestionRepository;

    @Mock
    private QuestionBankService questionBankService;

    private PaperTemplateService service;
    private long nextPaperId;
    private long nextQuestionId;

    @BeforeEach
    void setUp() {
        nextPaperId = 100L;
        nextQuestionId = 1000L;
        ExamPaperAccessService accessService = new ExamPaperAccessService(
                org.mockito.Mockito.mock(TeacherStudentService.class),
                org.mockito.Mockito.mock(ClassroomService.class));
        service = new PaperTemplateService(
                paperRepository,
                paperQuestionRepository,
                questionBankService,
                new ExamPaperSnapshotService(),
                accessService,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-29T01:30:00Z"), ZoneOffset.ofHours(8)));
        lenient().when(paperRepository.saveAndFlush(any(PaperTemplate.class))).thenAnswer(invocation -> {
            PaperTemplate paper = invocation.getArgument(0);
            if (paper.getId() == null) {
                paper.setId(nextPaperId++);
                paper.setCreatedAt(NOW);
            }
            paper.setUpdatedAt(NOW);
            return paper;
        });
        lenient().when(paperQuestionRepository.saveAndFlush(any(PaperTemplateQuestion.class)))
                .thenAnswer(invocation -> {
                    PaperTemplateQuestion question = invocation.getArgument(0);
                    if (question.getId() == null) {
                        question.setId(nextQuestionId++);
                        question.setCreatedAt(NOW);
                    }
                    return question;
                });
        lenient().when(paperQuestionRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<PaperTemplateQuestion> questions = invocation.getArgument(0);
            for (PaperTemplateQuestion question : questions) {
                if (question.getId() == null) {
                    question.setId(nextQuestionId++);
                    question.setCreatedAt(NOW);
                }
            }
            return questions;
        });
    }

    @Test
    void createDefaultsToDraftWithZeroTotalScore() {
        PaperTemplateResponse response = service.create(
                createRequest("Vocabulary quiz"), teacher(7L));

        assertEquals(PaperTemplateStatus.DRAFT, response.getStatus());
        assertEquals(new BigDecimal("0.00"), response.getTotalScore());
        assertEquals(7L, response.getOwnerUserId());
        assertTrue(response.getQuestions().isEmpty());
        assertEquals(NOW, response.getCreatedAt());
    }

    @Test
    void addingActiveQuestionCreatesIndependentSnapshotAndRecalculatesTotal() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.DRAFT);
        QuestionBankItem source = question(50L, QuestionBankItemStatus.ACTIVE, "Original stem", "2.50");
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(paper));
        when(questionBankService.findQuestionForPaper(50L)).thenReturn(source);
        when(paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(10L))
                .thenReturn(new ArrayList<>());

        PaperTemplateResponse response = service.addQuestion(
                10L, new AddPaperQuestionRequest(50L, new BigDecimal("3.25")), teacher(7L));
        source.setStem("Changed later");
        source.setAcceptedAnswersJson("[\"changed\"]");

        assertEquals(new BigDecimal("3.25"), response.getTotalScore());
        assertEquals("Original stem", response.getQuestions().get(0).getStem());
        assertEquals(List.of("answer"), response.getQuestions().get(0).getAcceptedAnswers());
        assertEquals(50L, response.getQuestions().get(0).getSourceQuestionId());
        assertEquals(1, response.getQuestions().get(0).getQuestionOrder());
        verify(paperRepository).findByIdForUpdate(10L);
    }

    @Test
    void addingArchivedQuestionIsRejected() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.DRAFT);
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(paper));
        when(questionBankService.findQuestionForPaper(50L))
                .thenReturn(question(50L, QuestionBankItemStatus.ARCHIVED, "Old", "1.00"));
        assertThrows(AccessDeniedException.class, () -> service.addQuestion(
                10L, new AddPaperQuestionRequest(50L, null), teacher(7L)));
        verify(paperRepository).findByIdForUpdate(10L);
        verify(paperQuestionRepository, never()).saveAndFlush(any());
    }

    @Test
    void reorderRequiresEveryQuestionExactlyOnceAndAssignsContiguousOrder() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.DRAFT);
        List<PaperTemplateQuestion> questions = new ArrayList<>(List.of(
                paperQuestion(101L, 10L, 1, "First", "1.00"),
                paperQuestion(102L, 10L, 2, "Second", "2.00"),
                paperQuestion(103L, 10L, 3, "Third", "3.00")));
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(paper));
        when(paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(10L))
                .thenReturn(questions);

        PaperTemplateResponse response = service.reorderQuestions(
                10L, new ReorderPaperQuestionsRequest(List.of(103L, 101L, 102L)), teacher(7L));

        assertEquals(List.of(103L, 101L, 102L), response.getQuestions().stream()
                .map(question -> question.getId()).toList());
        assertEquals(List.of(1, 2, 3), response.getQuestions().stream()
                .map(question -> question.getQuestionOrder()).toList());
        assertThrows(BadRequestException.class, () -> service.reorderQuestions(
                10L, new ReorderPaperQuestionsRequest(List.of(101L, 101L, 103L)), teacher(7L)));
        verify(paperRepository, times(2)).findByIdForUpdate(10L);
    }

    @Test
    void changingPaperSpecificScoreRecalculatesTotal() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.READY);
        List<PaperTemplateQuestion> questions = new ArrayList<>(List.of(
                paperQuestion(101L, 10L, 1, "First", "1.00"),
                paperQuestion(102L, 10L, 2, "Second", "2.00")));
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(paper));
        when(paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(10L))
                .thenReturn(questions);
        when(paperQuestionRepository.findByIdAndRemovedAtIsNull(102L))
                .thenReturn(Optional.of(questions.get(1)));

        PaperTemplateResponse response = service.updateQuestionScore(
                10L, 102L, new UpdatePaperQuestionScoreRequest(new BigDecimal("4.75")), teacher(7L));

        assertEquals(new BigDecimal("5.75"), response.getTotalScore());
        assertEquals(new BigDecimal("4.75"), response.getQuestions().get(1).getScore());
        verify(paperRepository).findByIdForUpdate(10L);
    }

    @Test
    void previewUsesStoredSnapshots() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.READY);
        List<PaperTemplateQuestion> questions = List.of(
                paperQuestion(101L, 10L, 1, "Frozen first", "1.00"),
                paperQuestion(102L, 10L, 2, "Frozen second", "2.00"));
        when(paperRepository.findById(10L)).thenReturn(Optional.of(paper));
        when(paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(10L))
                .thenReturn(questions);

        PaperTemplateResponse response = service.preview(10L, teacher(8L));

        assertEquals(List.of("Frozen first", "Frozen second"), response.getQuestions().stream()
                .map(question -> question.getStem()).toList());
    }

    @Test
    void copyReadyPaperCreatesIndependentDraftOwnedByActor() {
        PaperTemplate source = paper(10L, 8L, PaperTemplateStatus.READY);
        List<PaperTemplateQuestion> sourceQuestions = List.of(
                paperQuestion(101L, 10L, 1, "First", "1.25"),
                paperQuestion(102L, 10L, 2, "Second", "2.75"));
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(source));
        when(paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(10L))
                .thenReturn(sourceQuestions);

        PaperTemplateResponse response = service.copy(
                10L, new CopyPaperTemplateRequest("My copy"), teacher(7L));

        assertEquals(PaperTemplateStatus.DRAFT, response.getStatus());
        assertEquals(7L, response.getOwnerUserId());
        assertEquals(10L, response.getSourcePaperId());
        assertEquals("My copy", response.getTitle());
        assertEquals(new BigDecimal("4.00"), response.getTotalScore());
        assertNotEquals(101L, response.getQuestions().get(0).getId());
        assertEquals("First", response.getQuestions().get(0).getStem());
        verify(paperRepository).findByIdForUpdate(10L);
    }

    @Test
    void generatedCopyTitleNeverExceedsDatabaseLimit() {
        PaperTemplate source = paper(10L, 8L, PaperTemplateStatus.READY);
        source.setTitle("x".repeat(200));
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(source));
        when(paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(10L))
                .thenReturn(List.of());

        PaperTemplateResponse response = service.copy(10L, null, teacher(7L));

        assertEquals(200, response.getTitle().length());
    }

    @Test
    void nonOwnerCannotDirectlyEditPaper() {
        PaperTemplate paper = paper(10L, 8L, PaperTemplateStatus.DRAFT);
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(paper));

        assertThrows(AccessDeniedException.class, () -> service.update(
                10L, updateRequest("Stolen"), teacher(7L)));
        verify(paperRepository).findByIdForUpdate(10L);
        verify(paperRepository, never()).saveAndFlush(any());
    }

    @Test
    void archivedPaperCannotBeEditedAndOwnerCanArchiveDraft() {
        PaperTemplate archived = paper(10L, 7L, PaperTemplateStatus.ARCHIVED);
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(archived));

        assertThrows(BadRequestException.class, () -> service.update(
                10L, updateRequest("No changes"), teacher(7L)));

        PaperTemplate draft = paper(11L, 7L, PaperTemplateStatus.DRAFT);
        when(paperRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(draft));
        service.archive(11L, teacher(7L));

        assertEquals(PaperTemplateStatus.ARCHIVED, draft.getStatus());
        assertEquals(NOW, draft.getArchivedAt());
        verify(paperRepository).findByIdForUpdate(10L);
        verify(paperRepository).findByIdForUpdate(11L);
    }

    @Test
    void adminCanEditOnlyPapersOwnedBySameAdmin() {
        AppUser admin = user(1L, UserRole.ADMIN);
        PaperTemplate own = paper(10L, 1L, PaperTemplateStatus.DRAFT);
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(own));
        when(paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(10L))
                .thenReturn(List.of());

        PaperTemplateResponse updated = service.update(10L, updateRequest("Admin paper"), admin);
        service.archive(10L, admin);

        assertEquals("Admin paper", updated.getTitle());
        assertEquals(PaperTemplateStatus.ARCHIVED, own.getStatus());

        PaperTemplate other = paper(11L, 2L, PaperTemplateStatus.DRAFT);
        when(paperRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(other));
        assertThrows(AccessDeniedException.class, () -> service.update(
                11L, updateRequest("Forbidden"), admin));
        verify(paperRepository, times(2)).findByIdForUpdate(10L);
        verify(paperRepository).findByIdForUpdate(11L);
    }

    @Test
    void adminCanArchiveAnotherTeachersPaper() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.READY);
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(paper));

        service.archive(10L, user(1L, UserRole.ADMIN));

        assertEquals(PaperTemplateStatus.ARCHIVED, paper.getStatus());
        assertEquals(NOW, paper.getArchivedAt());
        verify(paperRepository).findByIdForUpdate(10L);
    }

    @Test
    void aggregateTotalMustFitNumericNineteenTwo() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.DRAFT);
        PaperTemplateQuestion existing = paperQuestion(
                101L, 10L, 1, "Max", "99999999999999999.99");
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(paper));
        when(questionBankService.findQuestionForPaper(50L))
                .thenReturn(question(50L, QuestionBankItemStatus.ACTIVE, "Overflow", "0.01"));
        when(paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(10L))
                .thenReturn(new ArrayList<>(List.of(existing)));

        assertThrows(BadRequestException.class, () -> service.addQuestion(
                10L, new AddPaperQuestionRequest(50L, new BigDecimal("0.01")), teacher(7L)));
        verify(paperRepository).findByIdForUpdate(10L);
        verify(paperRepository, never()).saveAndFlush(paper);
    }

    @Test
    void removeQuestionCompactsOrderAndRecalculatesTotal() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.DRAFT);
        List<PaperTemplateQuestion> questions = new ArrayList<>(List.of(
                paperQuestion(101L, 10L, 1, "First", "1.00"),
                paperQuestion(102L, 10L, 2, "Second", "2.00"),
                paperQuestion(103L, 10L, 3, "Third", "3.00")));
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(paper));
        when(paperQuestionRepository.findByIdAndRemovedAtIsNull(102L)).thenReturn(Optional.of(questions.get(1)));
        when(paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(10L))
                .thenReturn(questions);
        when(paperQuestionRepository.findMinimumQuestionOrder(10L)).thenReturn(1);

        PaperTemplateResponse response = service.removeQuestion(10L, 102L, teacher(7L));

        assertEquals(List.of(1, 2), response.getQuestions().stream()
                .map(question -> question.getQuestionOrder()).toList());
        assertEquals(new BigDecimal("4.00"), response.getTotalScore());
        assertEquals(NOW, questions.get(1).getRemovedAt());
        assertEquals(-1, questions.get(1).getQuestionOrder());
        verify(paperRepository).findByIdForUpdate(10L);
        verify(paperQuestionRepository, never()).delete(any());
    }

    @Test
    void publishingLocksAndReturnsOnlyActiveQuestionSnapshotsForOwner() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.READY);
        List<PaperTemplateQuestion> questions = List.of(
                paperQuestion(101L, 10L, 1, "First", "1.00"),
                paperQuestion(102L, 10L, 2, "Second", "2.00"));
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(paper));
        when(paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(10L))
                .thenReturn(questions);

        PaperTemplateService.PublicationSource source =
                service.lockReadyForPublishing(10L, teacher(7L));

        assertEquals(paper, source.paper());
        assertEquals(List.of(101L, 102L), source.questions().stream()
                .map(PaperTemplateQuestion::getId).toList());
        verify(paperRepository).findByIdForUpdate(10L);
    }

    @Test
    void publishingAllowsAdministratorToUseAnyReadyPaper() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.READY);
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(paper));
        when(paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(10L))
                .thenReturn(List.of(paperQuestion(101L, 10L, 1, "First", "1.00")));

        PaperTemplateService.PublicationSource source =
                service.lockReadyForPublishing(10L, user(1L, UserRole.ADMIN));

        assertEquals(10L, source.paper().getId());
    }

    @Test
    void publishingRejectsOtherTeacherNonReadyAndEmptyPapers() {
        PaperTemplate ready = paper(10L, 7L, PaperTemplateStatus.READY);
        PaperTemplate draft = paper(11L, 7L, PaperTemplateStatus.DRAFT);
        PaperTemplate empty = paper(12L, 7L, PaperTemplateStatus.READY);
        when(paperRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ready));
        when(paperRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(draft));
        when(paperRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(empty));
        when(paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(12L))
                .thenReturn(List.of());

        assertThrows(AccessDeniedException.class, () ->
                service.lockReadyForPublishing(10L, teacher(8L)));
        assertThrows(BadRequestException.class, () ->
                service.lockReadyForPublishing(11L, teacher(7L)));
        assertThrows(BadRequestException.class, () ->
                service.lockReadyForPublishing(12L, teacher(7L)));
    }

    private CreatePaperTemplateRequest createRequest(String title) {
        return new CreatePaperTemplateRequest(title, "Read carefully", false, true);
    }

    private UpdatePaperTemplateRequest updateRequest(String title) {
        return new UpdatePaperTemplateRequest(title, "Updated", true, false, PaperTemplateStatus.READY);
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

    private PaperTemplate paper(Long id, Long ownerId, PaperTemplateStatus status) {
        PaperTemplate paper = new PaperTemplate();
        paper.setId(id);
        paper.setTitle("Source paper");
        paper.setInstructions("Instructions");
        paper.setOwnerUserId(ownerId);
        paper.setStatus(status);
        paper.setShuffleQuestions(false);
        paper.setShuffleOptions(false);
        paper.setTotalScore(BigDecimal.ZERO);
        paper.setCreatedAt(NOW.minusDays(1));
        paper.setUpdatedAt(NOW.minusDays(1));
        return paper;
    }

    private QuestionBankItem question(
            Long id, QuestionBankItemStatus status, String stem, String score) {
        QuestionBankItem question = new QuestionBankItem();
        question.setId(id);
        question.setQuestionType(QuestionType.FILL_IN_BLANK);
        question.setStem(stem);
        question.setOptionsJson("{}");
        question.setAcceptedAnswersJson("[\"answer\"]");
        question.setExplanation("Because");
        question.setDefaultScore(new BigDecimal(score));
        question.setStatus(status);
        return question;
    }

    private PaperTemplateQuestion paperQuestion(
            Long id, Long paperId, int order, String stem, String score) {
        PaperTemplateQuestion question = new PaperTemplateQuestion();
        question.setId(id);
        question.setPaperTemplateId(paperId);
        question.setSourceQuestionId(id + 1000);
        question.setQuestionOrder(order);
        question.setQuestionType(QuestionType.FILL_IN_BLANK);
        question.setStem(stem);
        question.setOptionsJson("{}");
        question.setAcceptedAnswersJson("[\"answer\"]");
        question.setExplanation("Because");
        question.setScore(new BigDecimal(score));
        question.setCreatedAt(NOW.minusDays(1));
        return question;
    }
}
