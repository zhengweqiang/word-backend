package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.PointAdjustmentStatus;
import com.example.words.model.PointAttemptTriggerType;
import com.example.words.model.PointEventAttemptStatus;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointAccount;
import com.example.words.model.StudentPointAdjustmentRequest;
import com.example.words.model.StudentPointEvent;
import com.example.words.repository.StudentPointAccountRepository;
import com.example.words.repository.StudentPointAdjustmentRequestRepository;
import com.example.words.repository.StudentPointEventAttemptRepository;
import com.example.words.repository.StudentPointEventRepository;
import com.example.words.repository.StudentPointTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        StudentPointLedgerService.class,
        StudentPointEventService.class,
        StudentPointEventFactory.class,
        StudentPointEventCreationTransaction.class,
        StudentPointPostingTransaction.class,
        StudentPointFailureRecorder.class,
        StudentPointEventProcessor.class,
        StudentPointEventProcessingIntegrationTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentPointEventProcessingIntegrationTest {

    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();

    private static final Instant NOW = Instant.parse("2026-07-22T02:00:00Z");

    @Autowired
    private StudentPointEventService eventService;

    @Autowired
    private StudentPointPostingTransaction postingTransaction;

    @Autowired
    private StudentPointEventProcessor processor;

    @Autowired
    private StudentPointFailureRecorder failureRecorder;

    @Autowired
    private StudentPointAccountRepository accountRepository;

    @Autowired
    private StudentPointTransactionRepository transactionRepository;

    @SpyBean
    private StudentPointEventRepository eventRepository;

    @Autowired
    private StudentPointEventAttemptRepository attemptRepository;

    @Autowired
    private StudentPointAdjustmentRequestRepository adjustmentRepository;

    @BeforeEach
    void setUp() {
        attemptRepository.deleteAll();
        eventRepository.deleteAll();
        transactionRepository.deleteAll();
        adjustmentRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void autoClaimAndSuccessfulPostingShouldCommitAllBusinessRows() {
        StudentPointAccount account = accountRepository.saveAndFlush(StudentPointAccount.create(42L));
        StudentPointEvent event = eventRepository.saveAndFlush(event(42L, PointSourceType.STUDY_RECORD, 1));

        StudentPointEvent result = processor.process(
                event.getId(), StudentPointEventService.AttemptContext.auto());

        assertEquals(PointEventStatus.SUCCEEDED, result.getStatus());
        assertNotNull(result.getTransactionId());
        assertEquals(1, accountRepository.findById(account.getId()).orElseThrow().getAvailablePoints());
        assertEquals(1, transactionRepository.count());
        assertEquals(1, attemptRepository.count());
        assertEquals(PointEventAttemptStatus.SUCCEEDED,
                attemptRepository.findByEventIdOrderByAttemptNoAsc(event.getId()).get(0).getStatus());
    }

    @Test
    void manualAdjustmentSuccessShouldCloseRequestWithOriginalActor() {
        accountRepository.saveAndFlush(StudentPointAccount.create(42L));
        StudentPointAdjustmentRequest request = adjustment(42L, 8, "课堂贡献", 7L, "TEACHER");
        request = adjustmentRepository.saveAndFlush(request);
        StudentPointEvent event = manualEvent(request);
        event = eventRepository.saveAndFlush(event);

        StudentPointEvent result = processor.process(event.getId(),
                StudentPointEventService.AttemptContext.manual(99L, "ADMIN", "管理员重试"));

        StudentPointAdjustmentRequest reloaded = adjustmentRepository.findById(request.getId()).orElseThrow();
        assertEquals(PointAdjustmentStatus.APPLIED, reloaded.getStatus());
        assertEquals(result.getTransactionId(), reloaded.getTransactionId());
        assertNotNull(reloaded.getProcessedAt());
        assertEquals(7L, transactionRepository.findById(result.getTransactionId())
                .orElseThrow().getOperatorId());
    }

    @Test
    void latePostingFailureShouldRollbackLedgerAttemptEventAndAdjustmentClosure() {
        StudentPointAccount account = accountRepository.saveAndFlush(StudentPointAccount.create(42L));
        StudentPointAdjustmentRequest request = adjustment(42L, 8, "课堂贡献", 7L, "TEACHER");
        request = adjustmentRepository.saveAndFlush(request);
        StudentPointEvent event = eventRepository.saveAndFlush(manualEvent(request));
        eventService.claim(event.getId(), StudentPointEventService.AttemptContext.auto(), NOW);
        doThrow(new DataIntegrityViolationException("forced late event save failure"))
                .when(eventRepository).saveAndFlush(any(StudentPointEvent.class));

        assertThrows(DataIntegrityViolationException.class,
                () -> postingTransaction.post(event.getId(), NOW));

        assertEquals(0, accountRepository.findById(account.getId()).orElseThrow().getAvailablePoints());
        assertEquals(0, transactionRepository.count());
        assertEquals(0, attemptRepository.count());
        StudentPointEvent reloadedEvent = eventRepository.findById(event.getId()).orElseThrow();
        assertEquals(PointEventStatus.PROCESSING, reloadedEvent.getStatus());
        assertNull(reloadedEvent.getTransactionId());
        StudentPointAdjustmentRequest reloadedRequest = adjustmentRepository.findById(request.getId()).orElseThrow();
        assertEquals(PointAdjustmentStatus.PENDING, reloadedRequest.getStatus());
        assertNull(reloadedRequest.getTransactionId());
    }

    @Test
    void failedPostingShouldRollbackThenRecorderShouldCommitFailureInThirdTransaction() {
        StudentPointEvent event = eventRepository.saveAndFlush(event(404L, PointSourceType.STUDY_RECORD, 1));

        StudentPointEvent failed = processor.process(
                event.getId(), StudentPointEventService.AttemptContext.auto());

        assertEquals(PointEventStatus.FAILED, failed.getStatus());
        assertEquals(1, failed.getAutoAttemptCount());
        assertNotNull(failed.getNextRetryAt());
        assertEquals(0, transactionRepository.count());
        assertEquals(1, attemptRepository.count());
        assertEquals(PointEventAttemptStatus.FAILED,
                attemptRepository.findByEventIdOrderByAttemptNoAsc(event.getId()).get(0).getStatus());
    }

    @Test
    void manualFailureShouldNotConsumeAutomaticAttemptsAndManualCanRunAfterThree() {
        StudentPointEvent event = event(404L, PointSourceType.STUDY_RECORD, 1);
        event.setStatus(PointEventStatus.FAILED);
        event.setAutoAttemptCount(3);
        event = eventRepository.saveAndFlush(event);

        StudentPointEvent failed = processor.process(event.getId(),
                StudentPointEventService.AttemptContext.manual(9L, "ADMIN", "人工补发"));

        assertEquals(PointEventStatus.FAILED, failed.getStatus());
        assertEquals(3, failed.getAutoAttemptCount());
        assertEquals(PointAttemptTriggerType.MANUAL,
                attemptRepository.findByEventIdOrderByAttemptNoAsc(event.getId()).get(0).getTriggerType());
    }

    @Test
    void automaticFailuresShouldStopExactlyAtThree() {
        StudentPointEvent event = eventRepository.saveAndFlush(
                event(404L, PointSourceType.STUDY_RECORD, 1));

        for (int expected = 1; expected <= 3; expected++) {
            StudentPointEvent failed = processor.process(
                    event.getId(), StudentPointEventService.AttemptContext.auto());
            assertEquals(expected, failed.getAutoAttemptCount());
        }

        StudentPointEvent exhausted = eventRepository.findById(event.getId()).orElseThrow();
        assertNull(exhausted.getNextRetryAt());
        assertEquals(3, attemptRepository.count());
        StudentPointOperationException failure = assertThrows(
                StudentPointOperationException.class,
                () -> processor.process(
                        event.getId(), StudentPointEventService.AttemptContext.auto()));
        assertEquals("POINT_EVENT_AUTO_RETRY_EXHAUSTED", failure.getCode());
        assertEquals(3, attemptRepository.count());
    }

    @Test
    void restartAndTimeoutRecoveryShouldPersistExactErrorsAndStoredAttemptAudit() {
        StudentPointEvent automatic = processingEvent(404L, PointAttemptTriggerType.AUTO);
        automatic = eventRepository.saveAndFlush(automatic);
        StudentPointEvent recovered = failureRecorder.recover(
                automatic.getId(), "PROCESSING_INTERRUPTED_BY_SERVER_RESTART", NOW);

        assertEquals(PointEventStatus.FAILED, recovered.getStatus());
        assertEquals(1, recovered.getAutoAttemptCount());
        assertEquals(LocalDateTime.of(2026, 7, 22, 2, 0), recovered.getNextRetryAt());
        assertEquals("PROCESSING_INTERRUPTED_BY_SERVER_RESTART", recovered.getLastError());
        assertEquals("PROCESSING_INTERRUPTED_BY_SERVER_RESTART",
                attemptRepository.findByEventIdOrderByAttemptNoAsc(automatic.getId())
                        .get(0).getErrorMessage());

        StudentPointEvent manual = processingEvent(405L, PointAttemptTriggerType.MANUAL);
        manual.setAutoAttemptCount(2);
        manual.setIdempotencyKey("timeout-manual");
        manual = eventRepository.saveAndFlush(manual);
        StudentPointEvent timedOut = failureRecorder.recover(
                manual.getId(), "PROCESSING_TIMEOUT", NOW);

        assertEquals(2, timedOut.getAutoAttemptCount());
        assertEquals("PROCESSING_TIMEOUT", timedOut.getLastError());
        assertEquals(PointAttemptTriggerType.MANUAL,
                attemptRepository.findByEventIdOrderByAttemptNoAsc(manual.getId())
                        .get(0).getTriggerType());
        assertEquals(9L,
                attemptRepository.findByEventIdOrderByAttemptNoAsc(manual.getId())
                        .get(0).getOperatorId());
    }

    @Test
    void transactionalServicesShouldBeSpringProxiesWithRequiresNewBoundaries() throws Exception {
        assertTrue(AopUtils.isAopProxy(eventService));
        assertTrue(AopUtils.isAopProxy(postingTransaction));
        assertTrue(AopUtils.isAopProxy(failureRecorder));
        assertEquals(Propagation.REQUIRES_NEW,
                StudentPointEventService.class
                        .getMethod("claim", Long.class, StudentPointEventService.AttemptContext.class,
                                Instant.class)
                        .getAnnotation(Transactional.class)
                        .propagation());
        assertEquals(Propagation.REQUIRES_NEW,
                StudentPointPostingTransaction.class
                        .getMethod("post", Long.class, Instant.class)
                        .getAnnotation(Transactional.class)
                        .propagation());
        assertEquals(Propagation.REQUIRES_NEW,
                StudentPointFailureRecorder.class
                        .getMethod("recordFailure", Long.class, RuntimeException.class, Instant.class)
                        .getAnnotation(Transactional.class)
                        .propagation());
    }

    @Test
    void corruptManualAdjustmentLinkShouldFailEventAndAttemptWithoutChangingUnrelatedRequest() {
        StudentPointAdjustmentRequest unrelated = adjustment(99L, 5, "其他调整", 7L, "TEACHER");
        unrelated = adjustmentRepository.saveAndFlush(unrelated);
        StudentPointEvent event = processingEvent(42L, PointAttemptTriggerType.MANUAL);
        event.setSourceType(PointSourceType.MANUAL_ADJUSTMENT);
        event.setSourceId(null);
        event.setIdempotencyKey("manual-adjustment-missing");
        event = eventRepository.saveAndFlush(event);
        Long eventId = event.getId();

        StudentPointEvent failed = failureRecorder.recover(eventId, "PROCESSING_TIMEOUT", NOW);

        assertEquals(PointEventStatus.FAILED, failed.getStatus());
        assertEquals("MANUAL_ADJUSTMENT_STATE_INVALID", failed.getLastError());
        assertEquals(1, attemptRepository.count());
        assertEquals("MANUAL_ADJUSTMENT_STATE_INVALID",
                attemptRepository.findByEventIdOrderByAttemptNoAsc(eventId).get(0).getErrorMessage());
        assertEquals(0, accountRepository.count());
        assertEquals(0, transactionRepository.count());
        StudentPointAdjustmentRequest unchanged = adjustmentRepository.findById(unrelated.getId()).orElseThrow();
        assertEquals(PointAdjustmentStatus.PENDING, unchanged.getStatus());
        assertNull(unchanged.getProcessedAt());
    }

    @Test
    void absentOrMismatchedManualAdjustmentRequestShouldUseCorruptionFallback() {
        StudentPointEvent absent = processingEvent(42L, PointAttemptTriggerType.MANUAL);
        absent.setSourceType(PointSourceType.MANUAL_ADJUSTMENT);
        absent.setSourceId(99999L);
        absent.setIdempotencyKey("manual-adjustment-absent");
        absent = eventRepository.saveAndFlush(absent);

        StudentPointEvent absentFailed = failureRecorder.recover(
                absent.getId(), "PROCESSING_TIMEOUT", NOW);
        assertEquals("MANUAL_ADJUSTMENT_STATE_INVALID", absentFailed.getLastError());

        StudentPointAdjustmentRequest mismatched = adjustment(99L, 5, "其他调整", 7L, "TEACHER");
        mismatched = adjustmentRepository.saveAndFlush(mismatched);
        StudentPointEvent mismatch = processingEvent(42L, PointAttemptTriggerType.MANUAL);
        mismatch.setSourceType(PointSourceType.MANUAL_ADJUSTMENT);
        mismatch.setSourceId(mismatched.getId());
        mismatch.setIdempotencyKey("manual-adjustment-mismatch");
        mismatch = eventRepository.saveAndFlush(mismatch);

        StudentPointEvent mismatchFailed = failureRecorder.recover(
                mismatch.getId(), "PROCESSING_TIMEOUT", NOW);

        assertEquals("MANUAL_ADJUSTMENT_STATE_INVALID", mismatchFailed.getLastError());
        StudentPointAdjustmentRequest unchanged = adjustmentRepository.findById(mismatched.getId()).orElseThrow();
        assertEquals(PointAdjustmentStatus.PENDING, unchanged.getStatus());
        assertNull(unchanged.getProcessedAt());
    }

    @Test
    void matchingManualAdjustmentPostingFailureShouldFailEventAttemptAndRequestTogether() {
        StudentPointAdjustmentRequest request = adjustment(404L, 8, "补发课堂积分", 7L, "TEACHER");
        request = adjustmentRepository.saveAndFlush(request);
        StudentPointEvent event = eventRepository.saveAndFlush(manualEvent(request));

        StudentPointEvent failed = processor.process(
                event.getId(),
                StudentPointEventService.AttemptContext.manual(9L, "ADMIN", "管理员重试")
        );

        assertEquals(PointEventStatus.FAILED, failed.getStatus());
        assertEquals(0, failed.getAutoAttemptCount());
        StudentPointAdjustmentRequest reloaded = adjustmentRepository.findById(request.getId()).orElseThrow();
        assertEquals(PointAdjustmentStatus.FAILED, reloaded.getStatus());
        assertNotNull(reloaded.getProcessedAt());
        assertEquals(PointEventAttemptStatus.FAILED,
                attemptRepository.findByEventIdOrderByAttemptNoAsc(event.getId()).get(0).getStatus());
        assertEquals(PointAttemptTriggerType.MANUAL,
                attemptRepository.findByEventIdOrderByAttemptNoAsc(event.getId()).get(0).getTriggerType());
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void postingShouldRejectManualEventWhoseSourceIdAndKeysReferenceDifferentRequests() {
        StudentPointAccount account = accountRepository.saveAndFlush(StudentPointAccount.create(42L));
        StudentPointAdjustmentRequest first = adjustment(42L, 8, "补发课堂积分", 7L, "TEACHER");
        first = adjustmentRepository.saveAndFlush(first);
        StudentPointAdjustmentRequest second = adjustment(42L, 8, "补发课堂积分", 7L, "TEACHER");
        second = adjustmentRepository.saveAndFlush(second);
        StudentPointEvent event = manualEvent(first);
        event.setStatus(PointEventStatus.PROCESSING);
        event.setProcessingTriggerType(PointAttemptTriggerType.MANUAL);
        event.setProcessingOperatorId(9L);
        event.setProcessingOperatorRole("ADMIN");
        event.setProcessingReason("管理员重试");
        event.setProcessingStartedAt(LocalDateTime.of(2026, 7, 22, 1, 30));
        event.setSourceKey("manual-adjustment:" + second.getId());
        event.setIdempotencyKey("manual-adjustment:" + second.getId() + ":MANUAL_ADJUSTMENT");
        event = eventRepository.saveAndFlush(event);
        Long eventId = event.getId();

        StudentPointOperationException failure = assertThrows(
                StudentPointOperationException.class,
                () -> postingTransaction.post(eventId, NOW));

        assertEquals("MANUAL_ADJUSTMENT_STATE_INVALID", failure.getCode());
        assertEquals(0, accountRepository.findById(account.getId()).orElseThrow().getAvailablePoints());
        assertEquals(0, transactionRepository.count());
        assertEquals(0, attemptRepository.count());
        assertEquals(PointEventStatus.PROCESSING,
                eventRepository.findById(eventId).orElseThrow().getStatus());
        assertEquals(PointAdjustmentStatus.PENDING,
                adjustmentRepository.findById(first.getId()).orElseThrow().getStatus());
        assertEquals(PointAdjustmentStatus.PENDING,
                adjustmentRepository.findById(second.getId()).orElseThrow().getStatus());
    }

    @Test
    void failureRecorderShouldUseFallbackWhenManualSourceIdAndKeysDisagree() {
        StudentPointAdjustmentRequest first = adjustment(42L, 8, "补发课堂积分", 7L, "TEACHER");
        first = adjustmentRepository.saveAndFlush(first);
        StudentPointAdjustmentRequest second = adjustment(42L, 8, "补发课堂积分", 7L, "TEACHER");
        second = adjustmentRepository.saveAndFlush(second);
        StudentPointEvent event = manualEvent(first);
        event.setStatus(PointEventStatus.PROCESSING);
        event.setProcessingTriggerType(PointAttemptTriggerType.MANUAL);
        event.setProcessingOperatorId(9L);
        event.setProcessingOperatorRole("ADMIN");
        event.setProcessingReason("管理员重试");
        event.setProcessingStartedAt(LocalDateTime.of(2026, 7, 22, 1, 30));
        event.setSourceKey("manual-adjustment:" + second.getId());
        event.setIdempotencyKey("manual-adjustment:" + second.getId() + ":MANUAL_ADJUSTMENT");
        event = eventRepository.saveAndFlush(event);

        StudentPointEvent failed = failureRecorder.recover(
                event.getId(), "PROCESSING_TIMEOUT", NOW);

        assertEquals(PointEventStatus.FAILED, failed.getStatus());
        assertEquals("MANUAL_ADJUSTMENT_STATE_INVALID", failed.getLastError());
        assertEquals("MANUAL_ADJUSTMENT_STATE_INVALID",
                attemptRepository.findByEventIdOrderByAttemptNoAsc(event.getId()).get(0).getErrorMessage());
        assertEquals(PointAdjustmentStatus.PENDING,
                adjustmentRepository.findById(first.getId()).orElseThrow().getStatus());
        assertEquals(PointAdjustmentStatus.PENDING,
                adjustmentRepository.findById(second.getId()).orElseThrow().getStatus());
    }

    private StudentPointEvent event(Long studentId, PointSourceType sourceType, int points) {
        StudentPointEvent event = new StudentPointEvent();
        event.setStudentId(studentId);
        event.setSourceType(sourceType);
        event.setSourceId(88L);
        event.setSourceKey("record:88");
        event.setRuleCode("STUDY_RECORD_CORRECT");
        event.setRuleName("答对单词");
        event.setPoints(points);
        event.setIdempotencyKey("record:88:STUDY_RECORD_CORRECT:" + studentId);
        event.setStatus(PointEventStatus.PENDING);
        event.setAutoAttemptCount(0);
        event.setOperatorId(7L);
        event.setOperatorRole("TEACHER");
        event.setReason("原始业务原因");
        return event;
    }

    private StudentPointAdjustmentRequest adjustment(
            Long studentId,
            int amount,
            String reason,
            Long requestedBy,
            String requestedRole
    ) {
        return StudentPointAdjustmentRequest.create(
                "processing-" + REQUEST_SEQUENCE.incrementAndGet(),
                studentId,
                amount,
                reason,
                requestedBy,
                requestedRole,
                null
        );
    }

    private StudentPointEvent manualEvent(StudentPointAdjustmentRequest request) {
        StudentPointEvent event = event(request.getStudentId(), PointSourceType.MANUAL_ADJUSTMENT,
                request.getAmount());
        event.setSourceId(request.getId());
        event.setSourceKey("manual-adjustment:" + request.getId());
        event.setRuleCode("MANUAL_ADJUSTMENT");
        event.setRuleName("人工积分调整");
        event.setIdempotencyKey("manual-adjustment:" + request.getId() + ":MANUAL_ADJUSTMENT");
        event.setOperatorId(request.getRequestedBy());
        event.setOperatorRole(request.getRequestedRole());
        event.setReason(request.getReason());
        return event;
    }

    private StudentPointEvent processingEvent(Long studentId, PointAttemptTriggerType triggerType) {
        StudentPointEvent event = event(studentId, PointSourceType.STUDY_RECORD, 1);
        event.setStatus(PointEventStatus.PROCESSING);
        event.setProcessingTriggerType(triggerType);
        event.setProcessingOperatorId(triggerType == PointAttemptTriggerType.MANUAL ? 9L : null);
        event.setProcessingOperatorRole(triggerType == PointAttemptTriggerType.MANUAL ? "ADMIN" : null);
        event.setProcessingReason(triggerType == PointAttemptTriggerType.MANUAL ? "恢复失败任务" : null);
        event.setProcessingStartedAt(LocalDateTime.of(2026, 7, 22, 1, 30));
        return event;
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
