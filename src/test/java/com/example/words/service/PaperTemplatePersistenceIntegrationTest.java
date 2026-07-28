package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;

import com.example.words.dto.PaperTemplateResponse;
import com.example.words.dto.PaperTemplateSearchRequest;
import com.example.words.dto.ReorderPaperQuestionsRequest;
import com.example.words.model.AppUser;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperReleaseQuestion;
import com.example.words.model.PaperTemplate;
import com.example.words.model.PaperTemplateQuestion;
import com.example.words.model.PaperTemplateStatus;
import com.example.words.model.QuestionType;
import com.example.words.model.UserRole;
import com.example.words.repository.PaperReleaseQuestionRepository;
import com.example.words.repository.PaperReleaseRepository;
import com.example.words.repository.PaperTemplateQuestionRepository;
import com.example.words.repository.PaperTemplateRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaperTemplatePersistenceIntegrationTest {

    @Autowired
    private PaperTemplateRepository paperRepository;

    @Autowired
    private PaperTemplateQuestionRepository paperQuestionRepository;

    @Autowired
    private PaperReleaseRepository paperReleaseRepository;

    @Autowired
    private PaperReleaseQuestionRepository paperReleaseQuestionRepository;

    private PaperTemplateService service;

    @BeforeEach
    void setUp() {
        ExamPaperAccessService accessService = new ExamPaperAccessService(
                mock(TeacherStudentService.class), mock(ClassroomService.class));
        service = new PaperTemplateService(
                paperRepository,
                paperQuestionRepository,
                mock(QuestionBankService.class),
                new ExamPaperSnapshotService(),
                accessService,
                new ObjectMapper(),
                Clock.systemUTC());
    }

    @Test
    void teacherVisibilityIsOwnAnyStatusPlusOtherReady() {
        paperRepository.saveAllAndFlush(List.of(
                paper("own draft", 7L, PaperTemplateStatus.DRAFT),
                paper("own archived", 7L, PaperTemplateStatus.ARCHIVED),
                paper("other ready", 8L, PaperTemplateStatus.READY),
                paper("other draft", 8L, PaperTemplateStatus.DRAFT),
                paper("other archived", 8L, PaperTemplateStatus.ARCHIVED)));

        Page<PaperTemplateResponse> result = service.search(
                new PaperTemplateSearchRequest(), user(7L, UserRole.TEACHER));

        Set<String> titles = result.getContent().stream()
                .map(PaperTemplateResponse::getTitle)
                .collect(Collectors.toSet());
        assertEquals(Set.of("own draft", "own archived", "other ready"), titles);
    }

    @Test
    void repositoryBackedReorderSwapsUniqueOrdersSafely() {
        PaperTemplate paper = paperRepository.saveAndFlush(paper("quiz", 7L, PaperTemplateStatus.DRAFT));
        List<PaperTemplateQuestion> questions = paperQuestionRepository.saveAllAndFlush(List.of(
                question(paper.getId(), 1, "one"),
                question(paper.getId(), 2, "two"),
                question(paper.getId(), 3, "three")));

        service.reorderQuestions(
                paper.getId(),
                new ReorderPaperQuestionsRequest(List.of(
                        questions.get(2).getId(), questions.get(0).getId(), questions.get(1).getId())),
                user(7L, UserRole.TEACHER));

        List<PaperTemplateQuestion> reordered =
                paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(paper.getId());
        assertEquals(List.of("three", "one", "two"), reordered.stream()
                .map(PaperTemplateQuestion::getStem).toList());
        assertEquals(List.of(1, 2, 3), reordered.stream()
                .map(PaperTemplateQuestion::getQuestionOrder).toList());
    }

    @Test
    void referencedTemplateQuestionIsSoftRemovedAndReleaseReferenceRemains() {
        PaperTemplate paper = paperRepository.saveAndFlush(paper("quiz", 7L, PaperTemplateStatus.READY));
        PaperTemplateQuestion templateQuestion = paperQuestionRepository.saveAndFlush(
                question(paper.getId(), 1, "template"));
        PaperRelease release = new PaperRelease();
        release.setPaperTemplateId(paper.getId());
        release.setTitle("release");
        release.setPublishedByUserId(7L);
        release = paperReleaseRepository.saveAndFlush(release);
        PaperReleaseQuestion releaseQuestion = releaseQuestion(release.getId(), templateQuestion);
        releaseQuestion = paperReleaseQuestionRepository.saveAndFlush(releaseQuestion);

        service.removeQuestion(paper.getId(), templateQuestion.getId(), user(7L, UserRole.TEACHER));

        assertEquals(List.of(),
                paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(
                        paper.getId()));
        PaperTemplateQuestion removed = paperQuestionRepository.findById(templateQuestion.getId()).orElseThrow();
        assertNotNull(removed.getRemovedAt());
        assertEquals(-1, removed.getQuestionOrder());
        assertEquals(templateQuestion.getId(), paperReleaseQuestionRepository.findById(releaseQuestion.getId())
                .orElseThrow().getPaperTemplateQuestionId());
    }

    private PaperTemplate paper(String title, Long ownerId, PaperTemplateStatus status) {
        PaperTemplate paper = new PaperTemplate();
        paper.setTitle(title);
        paper.setOwnerUserId(ownerId);
        paper.setStatus(status);
        paper.setShuffleQuestions(false);
        paper.setShuffleOptions(false);
        paper.setTotalScore(BigDecimal.ZERO);
        return paper;
    }

    private PaperTemplateQuestion question(Long paperId, int order, String stem) {
        PaperTemplateQuestion question = new PaperTemplateQuestion();
        question.setPaperTemplateId(paperId);
        question.setQuestionOrder(order);
        question.setQuestionType(QuestionType.FILL_IN_BLANK);
        question.setStem(stem);
        question.setOptionsJson("{}");
        question.setAcceptedAnswersJson("[\"answer\"]");
        question.setScore(BigDecimal.ONE);
        return question;
    }

    private PaperReleaseQuestion releaseQuestion(Long releaseId, PaperTemplateQuestion templateQuestion) {
        PaperReleaseQuestion question = new PaperReleaseQuestion();
        question.setPaperReleaseId(releaseId);
        question.setPaperTemplateQuestionId(templateQuestion.getId());
        question.setQuestionOrder(templateQuestion.getQuestionOrder());
        question.setQuestionType(templateQuestion.getQuestionType());
        question.setStem(templateQuestion.getStem());
        question.setOptionsJson(templateQuestion.getOptionsJson());
        question.setAcceptedAnswersJson(templateQuestion.getAcceptedAnswersJson());
        question.setScore(templateQuestion.getScore());
        return question;
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
