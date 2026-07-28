package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.words.dto.ConfirmQuestionImportRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.QuestionImportBatch;
import com.example.words.model.QuestionImportBatchStatus;
import com.example.words.model.QuestionImportPreviewRow;
import com.example.words.model.QuestionImportPreviewRowStatus;
import com.example.words.model.QuestionType;
import com.example.words.model.UserRole;
import com.example.words.repository.QuestionBankItemRepository;
import com.example.words.repository.QuestionImportBatchRepository;
import com.example.words.repository.QuestionImportPreviewRowRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import({
        QuestionImportService.class,
        QuestionBankService.class,
        QuestionImportTransactionIntegrationTest.ImportTestConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QuestionImportTransactionIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-29T03:00:00Z");

    @Autowired
    private QuestionImportService service;

    @Autowired
    private QuestionImportBatchRepository batchRepository;

    @SpyBean
    private QuestionBankService questionBankService;

    @Autowired
    private QuestionImportPreviewRowRepository rowRepository;

    @Autowired
    private QuestionBankItemRepository questionRepository;

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        questionRepository.deleteAll();
        rowRepository.deleteAll();
        batchRepository.deleteAll();
    }

    @Test
    void simultaneousConfirmationCreatesOneQuestionAndRejectsTheLoser() throws Exception {
        QuestionImportBatch batch = saveBatch(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(1));
        QuestionImportPreviewRow row = saveValidRow(batch.getId(), 2, "Concurrent question");
        CountDownLatch bothCreating = new CountDownLatch(2);
        doAnswer(invocation -> {
            bothCreating.countDown();
            bothCreating.await(1, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(questionBankService).createImported(any(), anyLong(), any());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);
        Future<String> first = executor.submit(() -> confirm(batch.getId(), row.getId(), ready, start));
        Future<String> second = executor.submit(() -> confirm(batch.getId(), row.getId(), ready, start));
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        List<String> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

        assertEquals(1, outcomes.stream().filter("CONFIRMED"::equals).count());
        assertEquals(1, outcomes.stream().filter(value -> value.startsWith("REJECTED:")).count());
        assertTrue(outcomes.stream().anyMatch(value -> value.contains("already been confirmed")));
        assertEquals(1, questionRepository.count());
        assertEquals(QuestionImportBatchStatus.CONFIRMED,
                batchRepository.findById(batch.getId()).orElseThrow().getStatus());
    }

    @Test
    void expiredConfirmationPersistsExpiredStatusThroughSpringTransactionProxy() {
        QuestionImportBatch batch = saveBatch(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusSeconds(1));
        QuestionImportPreviewRow row = saveValidRow(batch.getId(), 2, "Expired question");

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.confirm(
                        batch.getId(), new ConfirmQuestionImportRequest(List.of(row.getId())), teacher()));

        assertEquals("Question import batch has expired", exception.getMessage());
        assertEquals(QuestionImportBatchStatus.EXPIRED,
                batchRepository.findById(batch.getId()).orElseThrow().getStatus());
        assertEquals(0, questionRepository.count());
    }

    @Test
    void midConfirmationFailureRollsBackEarlierQuestionsAndBatchTransition() {
        QuestionImportBatch batch = saveBatch(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(1));
        QuestionImportPreviewRow first = saveValidRow(batch.getId(), 2, "First question");
        QuestionImportPreviewRow second = saveValidRow(batch.getId(), 3, "Second question");
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) {
                throw new BadRequestException("Injected second-row failure");
            }
            return invocation.callRealMethod();
        }).when(questionBankService).createImported(any(), anyLong(), any());

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.confirm(
                        batch.getId(),
                        new ConfirmQuestionImportRequest(List.of(first.getId(), second.getId())),
                        teacher()));

        assertEquals("Injected second-row failure", exception.getMessage());
        assertEquals(0, questionRepository.count());
        QuestionImportBatch persisted = batchRepository.findById(batch.getId()).orElseThrow();
        assertEquals(QuestionImportBatchStatus.PREVIEWED, persisted.getStatus());
        assertNull(persisted.getConfirmedAt());
    }

    private String confirm(Long batchId, Long rowId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        try {
            service.confirm(batchId, new ConfirmQuestionImportRequest(List.of(rowId)), teacher());
            return "CONFIRMED";
        } catch (BadRequestException exception) {
            return "REJECTED:" + exception.getMessage();
        }
    }

    private QuestionImportBatch saveBatch(LocalDateTime expiresAt) {
        QuestionImportBatch batch = new QuestionImportBatch();
        batch.setImportedByUserId(7L);
        batch.setFileName("questions.csv");
        batch.setStatus(QuestionImportBatchStatus.PREVIEWED);
        batch.setExpiresAt(expiresAt);
        return batchRepository.saveAndFlush(batch);
    }

    private QuestionImportPreviewRow saveValidRow(Long batchId, int rowNumber, String stem) {
        QuestionImportPreviewRow row = new QuestionImportPreviewRow();
        row.setBatchId(batchId);
        row.setRowNumber(rowNumber);
        row.setStatus(QuestionImportPreviewRowStatus.VALID);
        row.setQuestionType(QuestionType.FILL_IN_BLANK);
        row.setStem(stem);
        row.setOptionsJson("{}");
        row.setAcceptedAnswersJson("[\"answer\"]");
        row.setScore(BigDecimal.ONE);
        row.setTags("[]");
        row.setRawRowJson("{}");
        return rowRepository.saveAndFlush(row);
    }

    private AppUser teacher() {
        AppUser actor = new AppUser();
        actor.setId(7L);
        actor.setRole(UserRole.TEACHER);
        return actor;
    }

    @TestConfiguration
    static class ImportTestConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        ExamPaperAnswerNormalizer examPaperAnswerNormalizer() {
            return new ExamPaperAnswerNormalizer();
        }

        @Bean
        ExamPaperAccessService examPaperAccessService() {
            return new ExamPaperAccessService(mock(TeacherStudentService.class), mock(ClassroomService.class));
        }
    }
}
