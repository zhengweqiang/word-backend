package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.words.dto.AddPaperQuestionRequest;
import com.example.words.dto.UpdatePaperTemplateRequest;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        when(questionBankService.findQuestionForPaper(anyLong())).thenAnswer(invocation -> {
            Long questionId = invocation.getArgument(0);
            if (questionId.equals(50L)) {
                firstHasLock.countDown();
                awaitSignal(releaseFirst, "first add release");
            }
            return question(questionId);
        });
        executor = Executors.newFixedThreadPool(2);
        Future<Long> first = executor.submit(() -> add(paper.getId(), 50L));
        awaitSignal(firstHasLock, "first add lock acquisition");
        Future<Long> second = executor.submit(() -> add(paper.getId(), 51L));
        try {
            assertNotNull(pollForDatabaseBlocker(), "H2 must report the second add waiting on the row lock");
        } finally {
            releaseFirst.countDown();
        }

        List<Long> addedIds = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        List<PaperTemplateQuestion> questions =
                paperQuestionRepository.findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(
                        paper.getId());

        assertEquals(2, addedIds.stream().distinct().count());
        assertEquals(List.of(1, 2), questions.stream()
                .map(PaperTemplateQuestion::getQuestionOrder).toList());
        assertEquals(2, questions.stream().map(PaperTemplateQuestion::getSourceQuestionId).distinct().count());
    }

    @Test
    void metadataUpdateCannotLoseConcurrentMembershipTotal() throws Exception {
        assertParentWriterPreservesConcurrentMembershipTotal(false);
    }

    @Test
    void archiveCannotLoseConcurrentMembershipTotal() throws Exception {
        assertParentWriterPreservesConcurrentMembershipTotal(true);
    }

    private void assertParentWriterPreservesConcurrentMembershipTotal(boolean archive) throws Exception {
        PaperTemplate paper = paperRepository.saveAndFlush(paper());
        CountDownLatch membershipHasLock = new CountDownLatch(1);
        CountDownLatch allowMembershipCommit = new CountDownLatch(1);
        when(questionBankService.findQuestionForPaper(60L)).thenAnswer(invocation -> {
            membershipHasLock.countDown();
            awaitSignal(allowMembershipCommit, "membership commit release");
            return question(60L);
        });
        executor = Executors.newFixedThreadPool(2);
        Future<?> membership = executor.submit(() -> service.addQuestion(
                paper.getId(), new AddPaperQuestionRequest(60L, BigDecimal.ONE), teacher()));
        awaitSignal(membershipHasLock, "membership lock acquisition");

        Future<?> parentWriter = executor.submit(() -> {
            if (archive) {
                service.archive(paper.getId(), teacher());
            } else {
                service.update(paper.getId(), updateRequest(), teacher());
            }
        });
        try {
            assertNotNull(pollForDatabaseBlocker(), "H2 must report the parent writer waiting on the row lock");
        } finally {
            allowMembershipCommit.countDown();
        }
        membership.get(10, TimeUnit.SECONDS);
        parentWriter.get(10, TimeUnit.SECONDS);

        PaperTemplate persisted = paperRepository.findById(paper.getId()).orElseThrow();
        assertEquals(new BigDecimal("1.00"), persisted.getTotalScore());
        if (archive) {
            assertEquals(PaperTemplateStatus.ARCHIVED, persisted.getStatus());
        } else {
            assertEquals("Updated metadata", persisted.getTitle());
        }
    }

    private Integer pollForDatabaseBlocker() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            List<Integer> blockerIds = jdbcTemplate.queryForList(
                    "SELECT BLOCKER_ID FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL",
                    Integer.class);
            if (!blockerIds.isEmpty()) {
                return blockerIds.get(0);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        } while (System.nanoTime() < deadline);
        return null;
    }

    private Long add(Long paperId, Long questionId) {
        return service.addQuestion(
                paperId, new AddPaperQuestionRequest(questionId, BigDecimal.ONE), teacher())
                .getQuestions().stream()
                .filter(question -> questionId.equals(question.getSourceQuestionId()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private void awaitSignal(CountDownLatch signal, String description) throws InterruptedException {
        if (!signal.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for " + description);
        }
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

    private UpdatePaperTemplateRequest updateRequest() {
        return new UpdatePaperTemplateRequest(
                "Updated metadata", "Updated", false, false, PaperTemplateStatus.READY);
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
