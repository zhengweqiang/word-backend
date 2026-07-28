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
        when(paperRepository.findById(10L)).thenReturn(Optional.of(paper));
        when(questionBankService.findQuestionForPaper(50L)).thenReturn(source);
        when(paperQuestionRepository.findByPaperTemplateIdOrderByQuestionOrderAsc(10L))
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
    }

    @Test
    void addingArchivedQuestionIsRejected() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.DRAFT);
        when(paperRepository.findById(10L)).thenReturn(Optional.of(paper));
        when(questionBankService.findQuestionForPaper(50L))
                .thenReturn(question(50L, QuestionBankItemStatus.ARCHIVED, "Old", "1.00"));
        assertThrows(AccessDeniedException.class, () -> service.addQuestion(
                10L, new AddPaperQuestionRequest(50L, null), teacher(7L)));
        verify(paperQuestionRepository, never()).saveAndFlush(any());
    }

    @Test
    void reorderRequiresEveryQuestionExactlyOnceAndAssignsContiguousOrder() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.DRAFT);
        List<PaperTemplateQuestion> questions = new ArrayList<>(List.of(
                paperQuestion(101L, 10L, 1, "First", "1.00"),
                paperQuestion(102L, 10L, 2, "Second", "2.00"),
                paperQuestion(103L, 10L, 3, "Third", "3.00")));
        when(paperRepository.findById(10L)).thenReturn(Optional.of(paper));
        when(paperQuestionRepository.findByPaperTemplateIdOrderByQuestionOrderAsc(10L)).thenReturn(questions);

        PaperTemplateResponse response = service.reorderQuestions(
                10L, new ReorderPaperQuestionsRequest(List.of(103L, 101L, 102L)), teacher(7L));

        assertEquals(List.of(103L, 101L, 102L), response.getQuestions().stream()
                .map(question -> question.getId()).toList());
        assertEquals(List.of(1, 2, 3), response.getQuestions().stream()
                .map(question -> question.getQuestionOrder()).toList());
        assertThrows(BadRequestException.class, () -> service.reorderQuestions(
                10L, new ReorderPaperQuestionsRequest(List.of(101L, 101L, 103L)), teacher(7L)));
    }

    @Test
    void changingPaperSpecificScoreRecalculatesTotal() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.READY);
        List<PaperTemplateQuestion> questions = new ArrayList<>(List.of(
                paperQuestion(101L, 10L, 1, "First", "1.00"),
                paperQuestion(102L, 10L, 2, "Second", "2.00")));
        when(paperRepository.findById(10L)).thenReturn(Optional.of(paper));
        when(paperQuestionRepository.findByPaperTemplateIdOrderByQuestionOrderAsc(10L)).thenReturn(questions);
        when(paperQuestionRepository.findById(102L)).thenReturn(Optional.of(questions.get(1)));

        PaperTemplateResponse response = service.updateQuestionScore(
                10L, 102L, new UpdatePaperQuestionScoreRequest(new BigDecimal("4.75")), teacher(7L));

        assertEquals(new BigDecimal("5.75"), response.getTotalScore());
        assertEquals(new BigDecimal("4.75"), response.getQuestions().get(1).getScore());
    }

    @Test
    void previewUsesStoredSnapshots() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.READY);
        List<PaperTemplateQuestion> questions = List.of(
                paperQuestion(101L, 10L, 1, "Frozen first", "1.00"),
                paperQuestion(102L, 10L, 2, "Frozen second", "2.00"));
        when(paperRepository.findById(10L)).thenReturn(Optional.of(paper));
        when(paperQuestionRepository.findByPaperTemplateIdOrderByQuestionOrderAsc(10L)).thenReturn(questions);

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
        when(paperRepository.findById(10L)).thenReturn(Optional.of(source));
        when(paperQuestionRepository.findByPaperTemplateIdOrderByQuestionOrderAsc(10L))
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
    }

    @Test
    void nonOwnerCannotDirectlyEditPaper() {
        PaperTemplate paper = paper(10L, 8L, PaperTemplateStatus.DRAFT);
        when(paperRepository.findById(10L)).thenReturn(Optional.of(paper));

        assertThrows(AccessDeniedException.class, () -> service.update(
                10L, updateRequest("Stolen"), teacher(7L)));
        verify(paperRepository, never()).saveAndFlush(any());
    }

    @Test
    void archivedPaperCannotBeEditedAndOwnerCanArchiveDraft() {
        PaperTemplate archived = paper(10L, 7L, PaperTemplateStatus.ARCHIVED);
        when(paperRepository.findById(10L)).thenReturn(Optional.of(archived));

        assertThrows(BadRequestException.class, () -> service.update(
                10L, updateRequest("No changes"), teacher(7L)));

        PaperTemplate draft = paper(11L, 7L, PaperTemplateStatus.DRAFT);
        when(paperRepository.findById(11L)).thenReturn(Optional.of(draft));
        service.archive(11L, teacher(7L));

        assertEquals(PaperTemplateStatus.ARCHIVED, draft.getStatus());
        assertEquals(NOW, draft.getArchivedAt());
    }

    @Test
    void removeQuestionCompactsOrderAndRecalculatesTotal() {
        PaperTemplate paper = paper(10L, 7L, PaperTemplateStatus.DRAFT);
        List<PaperTemplateQuestion> questions = new ArrayList<>(List.of(
                paperQuestion(101L, 10L, 1, "First", "1.00"),
                paperQuestion(102L, 10L, 2, "Second", "2.00"),
                paperQuestion(103L, 10L, 3, "Third", "3.00")));
        when(paperRepository.findById(10L)).thenReturn(Optional.of(paper));
        when(paperQuestionRepository.findById(102L)).thenReturn(Optional.of(questions.get(1)));
        when(paperQuestionRepository.findByPaperTemplateIdOrderByQuestionOrderAsc(10L))
                .thenReturn(questions);

        PaperTemplateResponse response = service.removeQuestion(10L, 102L, teacher(7L));

        assertEquals(List.of(1, 2), response.getQuestions().stream()
                .map(question -> question.getQuestionOrder()).toList());
        assertEquals(new BigDecimal("4.00"), response.getTotalScore());
        verify(paperQuestionRepository).delete(questions.get(1));
    }

    private CreatePaperTemplateRequest createRequest(String title) {
        return new CreatePaperTemplateRequest(title, "Read carefully", false, true);
    }

    private UpdatePaperTemplateRequest updateRequest(String title) {
        return new UpdatePaperTemplateRequest(title, "Updated", true, false, PaperTemplateStatus.READY);
    }

    private AppUser teacher(Long id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(UserRole.TEACHER);
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
