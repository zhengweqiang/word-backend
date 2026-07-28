package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.words.dto.AddPaperQuestionRequest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import({
        PaperTemplateService.class,
        ExamPaperSnapshotService.class,
        PaperTemplateTransactionIntegrationTest.PaperTestConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaperTemplateTransactionIntegrationTest {

    @Autowired
    private PaperTemplateService service;

    @Autowired
    private PaperTemplateRepository paperRepository;

    @Autowired
    private PaperTemplateQuestionRepository paperQuestionRepository;

    @MockBean
    private QuestionBankService questionBankService;

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        paperQuestionRepository.deleteAll();
        paperRepository.deleteAll();
    }

    @Test
    void simultaneousAddsBothSucceedWithDistinctContiguousOrders() throws Exception {
        PaperTemplate paper = paperRepository.saveAndFlush(paper());
        when(questionBankService.findQuestionForPaper(anyLong())).thenAnswer(invocation ->
                question(invocation.getArgument(0)));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);
        Future<Long> first = executor.submit(() -> add(paper.getId(), 50L, ready, start));
        Future<Long> second = executor.submit(() -> add(paper.getId(), 51L, ready, start));
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        List<Long> addedIds = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        List<PaperTemplateQuestion> questions =
                paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(
                        paper.getId());

        assertEquals(2, addedIds.stream().distinct().count());
        assertEquals(List.of(1, 2), questions.stream()
                .map(PaperTemplateQuestion::getQuestionOrder).toList());
        assertEquals(2, questions.stream().map(PaperTemplateQuestion::getSourceQuestionId).distinct().count());
    }

    private Long add(Long paperId, Long questionId, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return service.addQuestion(
                paperId, new AddPaperQuestionRequest(questionId, BigDecimal.ONE), teacher())
                .getQuestions().stream()
                .filter(question -> questionId.equals(question.getSourceQuestionId()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private PaperTemplate paper() {
        PaperTemplate paper = new PaperTemplate();
        paper.setTitle("Concurrent paper");
        paper.setOwnerUserId(7L);
        paper.setStatus(PaperTemplateStatus.DRAFT);
        paper.setShuffleQuestions(false);
        paper.setShuffleOptions(false);
        paper.setTotalScore(BigDecimal.ZERO);
        return paper;
    }

    private QuestionBankItem question(Long id) {
        QuestionBankItem question = new QuestionBankItem();
        question.setId(id);
        question.setQuestionType(QuestionType.FILL_IN_BLANK);
        question.setStem("Question " + id);
        question.setOptionsJson("{}");
        question.setAcceptedAnswersJson("[\"answer\"]");
        question.setDefaultScore(BigDecimal.ONE);
        question.setStatus(QuestionBankItemStatus.ACTIVE);
        return question;
    }

    private AppUser teacher() {
        AppUser actor = new AppUser();
        actor.setId(7L);
        actor.setRole(UserRole.TEACHER);
        return actor;
    }

    @TestConfiguration
    static class PaperTestConfiguration {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        ExamPaperAccessService examPaperAccessService() {
            return new ExamPaperAccessService(mock(TeacherStudentService.class), mock(ClassroomService.class));
        }
    }
}
