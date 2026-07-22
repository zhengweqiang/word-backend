package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.AppUser;
import com.example.words.model.PointAdjustmentStatus;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointAccount;
import com.example.words.model.StudentPointAdjustmentRequest;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.UserRole;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.StudentPointAccountRepository;
import com.example.words.repository.StudentPointAdjustmentRequestRepository;
import com.example.words.repository.StudentPointEventAttemptRepository;
import com.example.words.repository.StudentPointEventRepository;
import com.example.words.repository.StudentPointTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        StudentPointAdjustmentService.class,
        StudentPointAdjustmentTransaction.class,
        StudentPointEventFactory.class,
        StudentPointEventCreationTransaction.class,
        StudentPointEventService.class,
        StudentPointEventProcessor.class,
        StudentPointPostingTransaction.class,
        StudentPointFailureRecorder.class,
        StudentPointLedgerService.class,
        StudentPointAdjustmentIdempotencyIntegrationTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentPointAdjustmentIdempotencyIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-22T07:00:00Z");
    private static final String REQUEST_KEY = "ad0d7910-f61a-439c-8ee4-bf6af607d065";

    @Autowired
    private StudentPointAdjustmentService service;
    @Autowired
    private StudentPointAdjustmentRequestRepository requestRepository;
    @Autowired
    private StudentPointEventRepository eventRepository;
    @Autowired
    private StudentPointEventAttemptRepository attemptRepository;
    @Autowired
    private StudentPointTransactionRepository pointTransactionRepository;
    @Autowired
    private StudentPointAccountRepository accountRepository;
    @Autowired
    private AppUserRepository userRepository;
    @SpyBean
    private StudentPointAdjustmentTransaction adjustmentTransaction;
    @MockBean
    private TeacherStudentService teacherStudentService;

    private ExecutorService executor;
    private AppUser admin;
    private AppUser student;

    @BeforeEach
    void setUp() {
        attemptRepository.deleteAll();
        eventRepository.deleteAll();
        pointTransactionRepository.deleteAll();
        requestRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        admin = userRepository.saveAndFlush(user("admin", UserRole.ADMIN));
        student = userRepository.saveAndFlush(user("student", UserRole.STUDENT));
        accountRepository.saveAndFlush(StudentPointAccount.create(student.getId()));
        clearInvocations(adjustmentTransaction);
    }

    @AfterEach
    void stopExecutor() throws InterruptedException {
        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void repeatedSamePayloadCreatesAndProcessesExactlyOnce() {
        StudentPointAdjustmentService.AdjustmentCommand command = command(REQUEST_KEY, 9, "reward", null);

        StudentPointAdjustmentService.AdjustmentOutcome first = service.adjust(admin, command);
        StudentPointAdjustmentService.AdjustmentOutcome replay = service.adjust(admin, command);

        assertEquals(first, replay);
        assertEquals(PointAdjustmentStatus.APPLIED, replay.status());
        assertEquals(1, requestRepository.count());
        assertEquals(1, eventRepository.count());
        assertEquals(1, attemptRepository.count());
        assertEquals(1, pointTransactionRepository.count());
        assertEquals(9, accountRepository.findByStudentId(student.getId()).orElseThrow().getAvailablePoints());
    }

    @Test
    void repeatedKeyWithDifferentPayloadConflictsWithoutSideEffects() {
        service.adjust(admin, command(REQUEST_KEY, 9, "reward", null));

        StudentPointOperationException failure = assertThrows(StudentPointOperationException.class,
                () -> service.adjust(admin, command(REQUEST_KEY, 10, "reward", null)));

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", failure.getCode());
        assertEquals(1, requestRepository.count());
        assertEquals(1, eventRepository.count());
        assertEquals(1, pointTransactionRepository.count());
    }

    @Test
    void committedPendingWorkflowIsRecoveredAndFailedTerminalWorkflowIsOnlyReplayed() {
        StudentPointAdjustmentTransaction.Workflow pending = adjustmentTransaction.createWorkflow(
                new StudentPointAdjustmentTransaction.CreateCommand(
                        REQUEST_KEY, student.getId(), 7, "recovery", admin, null));

        StudentPointAdjustmentService.AdjustmentOutcome recovered = service.adjust(
                admin, command(REQUEST_KEY, 7, "recovery", null));
        assertEquals(PointAdjustmentStatus.APPLIED, recovered.status());
        assertEquals(1, attemptRepository.count());

        StudentPointAdjustmentRequest request = requestRepository.findById(pending.request().getId()).orElseThrow();
        StudentPointEvent event = eventRepository.findById(pending.event().getId()).orElseThrow();
        request.setStatus(PointAdjustmentStatus.FAILED);
        request.setTransactionId(null);
        event.setStatus(PointEventStatus.FAILED);
        event.setTransactionId(null);
        requestRepository.saveAndFlush(request);
        eventRepository.saveAndFlush(event);

        StudentPointAdjustmentService.AdjustmentOutcome replay = service.adjust(
                admin, command(REQUEST_KEY, 7, "recovery", null));
        assertEquals(PointAdjustmentStatus.FAILED, replay.status());
        assertEquals(1, attemptRepository.count());
        assertEquals(1, pointTransactionRepository.count());
    }

    @Test
    void concurrentSameKeyRaceHasOneRequestEventAttemptAndLedgerEntry() throws Exception {
        CountDownLatch bothInsideCreation = new CountDownLatch(2);
        doAnswer(invocation -> {
            bothInsideCreation.countDown();
            if (!bothInsideCreation.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent callers did not meet");
            }
            return invocation.callRealMethod();
        }).when(adjustmentTransaction).createWorkflow(org.mockito.ArgumentMatchers.any());
        executor = Executors.newFixedThreadPool(2);
        Callable<StudentPointAdjustmentService.AdjustmentOutcome> call =
                () -> service.adjust(admin, command(REQUEST_KEY, 11, "concurrent", null));

        List<Future<StudentPointAdjustmentService.AdjustmentOutcome>> results =
                List.of(executor.submit(call), executor.submit(call));
        for (Future<StudentPointAdjustmentService.AdjustmentOutcome> result : results) {
            result.get(20, TimeUnit.SECONDS);
        }

        assertEquals(1, requestRepository.count());
        assertEquals(1, eventRepository.count());
        assertEquals(1, attemptRepository.count());
        assertEquals(1, pointTransactionRepository.count());
        assertEquals(11, accountRepository.findByStudentId(student.getId()).orElseThrow().getAvailablePoints());
    }

    @Test
    void replacementThroughRealServiceClosesOldWorkflowAndReplaysWithoutDuplicatePosting() {
        StudentPointAdjustmentTransaction.Workflow old = adjustmentTransaction.createWorkflow(
                new StudentPointAdjustmentTransaction.CreateCommand(
                        "old-failed-key", student.getId(), 5, "old", admin, null));
        StudentPointAdjustmentRequest oldRequest = requestRepository.findById(old.request().getId()).orElseThrow();
        StudentPointEvent oldEvent = eventRepository.findById(old.event().getId()).orElseThrow();
        oldRequest.setStatus(PointAdjustmentStatus.FAILED);
        oldEvent.setStatus(PointEventStatus.FAILED);
        oldEvent.setAutoAttemptCount(3);
        requestRepository.saveAndFlush(oldRequest);
        eventRepository.saveAndFlush(oldEvent);
        StudentPointAdjustmentService.AdjustmentCommand replacement =
                command("replacement-service-key", 8, "corrected", oldRequest.getId());

        StudentPointAdjustmentService.AdjustmentOutcome applied = service.adjust(admin, replacement);
        StudentPointAdjustmentService.AdjustmentOutcome replay = service.adjust(admin, replacement);

        assertEquals(applied, replay);
        StudentPointAdjustmentRequest reloadedOld = requestRepository.findById(oldRequest.getId()).orElseThrow();
        StudentPointAdjustmentRequest reloadedNew = requestRepository.findById(applied.requestId()).orElseThrow();
        StudentPointEvent reloadedOldEvent = eventRepository.findById(oldEvent.getId()).orElseThrow();
        assertEquals(PointAdjustmentStatus.REJECTED, reloadedOld.getStatus());
        assertEquals(reloadedNew.getId(), reloadedOld.getReplacedByRequestId());
        assertEquals(reloadedOld.getId(), reloadedNew.getReplacesRequestId());
        assertEquals(PointEventStatus.CANCELLED, reloadedOldEvent.getStatus());
        assertEquals(2, requestRepository.count());
        assertEquals(2, eventRepository.count());
        assertEquals(1, attemptRepository.count());
        assertEquals(1, pointTransactionRepository.count());
        assertEquals(8, accountRepository.findByStudentId(student.getId()).orElseThrow().getAvailablePoints());
    }

    private StudentPointAdjustmentService.AdjustmentCommand command(
            String requestKey,
            int amount,
            String reason,
            Long replacesRequestId
    ) {
        return new StudentPointAdjustmentService.AdjustmentCommand(
                requestKey, student.getId(), amount, reason, replacesRequestId);
    }

    private AppUser user(String username, UserRole role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash("hash");
        user.setDisplayName(username);
        user.setRole(role);
        return user;
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
