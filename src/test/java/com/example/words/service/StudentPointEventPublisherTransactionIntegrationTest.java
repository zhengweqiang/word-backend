package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointRule;
import com.example.words.repository.StudentPointRuleRepository;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentPointEventPublisherTransactionIntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private StudentPointRuleRepository ruleRepository;

    private StudentPointEventService eventService;
    private StudentPointEventPublisher publisher;
    private ControllableTaskExecutor taskExecutor;

    @BeforeEach
    void setUp() {
        eventService = mock(StudentPointEventService.class);
        taskExecutor = new ControllableTaskExecutor();
        publisher = new StudentPointEventPublisher(eventService, taskExecutor);
    }

    @Test
    void shouldDeferCreationUntilTransactionCommits() {
        StudentPointEventPublisher.PublishRequest request = request(201L);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            publisher.publishAfterCommit(request);
            verify(eventService, never()).create(org.mockito.ArgumentMatchers.any());
        });

        verify(eventService, never()).create(org.mockito.ArgumentMatchers.any());
        taskExecutor.runNext();
        verify(eventService).create(eventRequest(201L));
    }

    @Test
    void shouldNotCreateEventWhenTransactionRollsBack() {
        StudentPointEventPublisher.PublishRequest request = request(202L);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            publisher.publishAfterCommit(request);
            status.setRollbackOnly();
        });

        verify(eventService, never()).create(org.mockito.ArgumentMatchers.any());
        taskExecutor.assertEmpty();
    }

    @Test
    void rejectedAfterCommitSubmissionShouldNotChangeCommittedOutcome() {
        StudentPointEventPublisher rejectingPublisher = new StudentPointEventPublisher(
                eventService,
                command -> {
                    throw new RejectedExecutionException("queue full");
                }
        );
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertDoesNotThrow(() -> transaction.executeWithoutResult(status -> {
            ruleRepository.save(StudentPointRule.create(
                    "COMMITTED_RULE",
                    "Committed rule",
                    PointSourceType.STUDY_RECORD,
                    1
            ));
            rejectingPublisher.publishAfterCommit(request(203L));
        }));

        org.junit.jupiter.api.Assertions.assertTrue(ruleRepository.findByCode("COMMITTED_RULE").isPresent());
        verify(eventService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void springTaskRejectionAfterCommitShouldNotChangeCommittedOutcome() {
        StudentPointEventPublisher rejectingPublisher = new StudentPointEventPublisher(
                eventService,
                command -> {
                    throw new TaskRejectedException("queue full");
                }
        );
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertDoesNotThrow(() -> transaction.executeWithoutResult(status -> {
            ruleRepository.save(StudentPointRule.create(
                    "SPRING_REJECTION_RULE",
                    "Spring rejection rule",
                    PointSourceType.STUDY_RECORD,
                    1
            ));
            rejectingPublisher.publishAfterCommit(request(205L));
        }));

        org.junit.jupiter.api.Assertions.assertTrue(ruleRepository.findByCode("SPRING_REJECTION_RULE").isPresent());
        verify(eventService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void asynchronousCreationFailureShouldNotChangeCommittedOutcome() {
        when(eventService.create(eventRequest(204L))).thenThrow(new IllegalStateException("event create failed"));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            ruleRepository.save(StudentPointRule.create(
                    "ASYNC_FAILURE_RULE",
                    "Async failure rule",
                    PointSourceType.STUDY_RECORD,
                    1
            ));
            publisher.publishAfterCommit(request(204L));
        });

        assertDoesNotThrow(taskExecutor::runNext);
        org.junit.jupiter.api.Assertions.assertTrue(ruleRepository.findByCode("ASYNC_FAILURE_RULE").isPresent());
    }

    private StudentPointEventPublisher.PublishRequest request(Long sourceId) {
        return new StudentPointEventPublisher.PublishRequest(
                20L,
                sourceId,
                "study-record:" + sourceId + ":correct",
                "STUDY_RECORD_CORRECT"
        );
    }

    private StudentPointEventService.CreateRequest eventRequest(Long sourceId) {
        return new StudentPointEventService.CreateRequest(
                20L,
                sourceId,
                "study-record:" + sourceId + ":correct",
                "STUDY_RECORD_CORRECT",
                null,
                null
        );
    }

    private static final class ControllableTaskExecutor implements TaskExecutor {

        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        private void runNext() {
            Runnable task = tasks.remove();
            task.run();
        }

        private void assertEmpty() {
            org.junit.jupiter.api.Assertions.assertTrue(tasks.isEmpty());
        }
    }
}
