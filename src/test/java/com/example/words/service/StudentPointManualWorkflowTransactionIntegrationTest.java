package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.AppUser;
import com.example.words.model.PointAdjustmentStatus;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointAccount;
import com.example.words.model.StudentPointAdjustmentRequest;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.UserRole;
import com.example.words.repository.StudentPointAccountRepository;
import com.example.words.repository.StudentPointAdjustmentRequestRepository;
import com.example.words.repository.StudentPointEventAttemptRepository;
import com.example.words.repository.StudentPointEventRepository;
import com.example.words.repository.StudentPointTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        StudentPointAdjustmentTransaction.class,
        StudentPointAdminTransaction.class,
        StudentPointLedgerService.class,
        StudentPointEventFactory.class,
        StudentPointEventCreationTransaction.class,
        StudentPointEventService.class,
        StudentPointPostingTransaction.class,
        StudentPointFailureRecorder.class,
        StudentPointEventProcessor.class,
        StudentPointManualWorkflowTransactionIntegrationTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentPointManualWorkflowTransactionIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-22T04:00:00Z");
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();

    @Autowired
    private StudentPointAdjustmentTransaction adjustmentTransaction;

    @Autowired
    private StudentPointAdminTransaction adminTransaction;

    @Autowired
    private StudentPointEventProcessor processor;

    @Autowired
    private StudentPointAdjustmentRequestRepository requestRepository;

    @Autowired
    private StudentPointEventRepository eventRepository;

    @Autowired
    private StudentPointEventAttemptRepository attemptRepository;

    @Autowired
    private StudentPointTransactionRepository transactionRepository;

    @Autowired
    private StudentPointAccountRepository accountRepository;

    @BeforeEach
    void clean() {
        attemptRepository.deleteAll();
        eventRepository.deleteAll();
        transactionRepository.deleteAll();
        requestRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void replacementCancelsOldBeforeNewEventIsProcessed() {
        AppUser teacher = actor(7L, UserRole.TEACHER);
        accountRepository.saveAndFlush(StudentPointAccount.create(42L));
        StudentPointAdjustmentRequest oldRequest = request(42L, 5, teacher, PointAdjustmentStatus.FAILED);
        oldRequest = requestRepository.saveAndFlush(oldRequest);
        StudentPointEvent oldEvent = event(oldRequest, PointEventStatus.FAILED, 3);
        oldEvent = eventRepository.saveAndFlush(oldEvent);
        StudentPointAdjustmentTransaction.Workflow replacement = adjustmentTransaction.createWorkflow(
                command("replacement-success", 42L, 8, "corrected", teacher, oldRequest.getId()));
        StudentPointAdjustmentRequest newRequest = replacement.request();
        StudentPointEvent created = replacement.event();
        StudentPointEvent applied = processor.process(
                created.getId(),
                StudentPointEventService.AttemptContext.manual(teacher.getId(), teacher.getRole().name(), "corrected")
        );

        StudentPointEvent reloadedOldEvent = eventRepository.findById(oldEvent.getId()).orElseThrow();
        StudentPointAdjustmentRequest reloadedOldRequest = requestRepository.findById(oldRequest.getId()).orElseThrow();
        StudentPointAdjustmentRequest reloadedNewRequest = requestRepository.findById(newRequest.getId()).orElseThrow();
        assertEquals(PointEventStatus.CANCELLED, reloadedOldEvent.getStatus());
        assertEquals(PointAdjustmentStatus.REJECTED, reloadedOldRequest.getStatus());
        assertEquals(PointEventStatus.SUCCEEDED, applied.getStatus());
        assertEquals(PointAdjustmentStatus.APPLIED, reloadedNewRequest.getStatus());
        assertNotNull(reloadedNewRequest.getTransactionId());
        assertEquals(8, accountRepository.findByStudentId(42L).orElseThrow().getAvailablePoints());
    }

    @Test
    void replacementStateMatrixRejectsPendingProcessingFailedBeforeThreeAndSucceeded() {
        AppUser admin = actor(1L, UserRole.ADMIN);
        assertReplacementCode(admin, PointEventStatus.PENDING, 0, PointAdjustmentStatus.PENDING,
                "POINT_ADJUSTMENT_RETRY_IN_PROGRESS");
        clean();
        assertReplacementCode(admin, PointEventStatus.PROCESSING, 0, PointAdjustmentStatus.PENDING,
                "POINT_ADJUSTMENT_RETRY_IN_PROGRESS");
        clean();
        assertReplacementCode(admin, PointEventStatus.FAILED, 2, PointAdjustmentStatus.FAILED,
                "POINT_ADJUSTMENT_RETRY_IN_PROGRESS");
        clean();
        assertReplacementCode(admin, PointEventStatus.SUCCEEDED, 3, PointAdjustmentStatus.APPLIED,
                "POINT_ADJUSTMENT_ALREADY_APPLIED");
    }

    @Test
    void duplicateReplacementIsRejected() {
        AppUser admin = actor(1L, UserRole.ADMIN);
        StudentPointAdjustmentRequest oldRequest = requestRepository.saveAndFlush(
                request(42L, 5, admin, PointAdjustmentStatus.FAILED));
        StudentPointEvent oldEvent = eventRepository.saveAndFlush(event(oldRequest, PointEventStatus.FAILED, 3));
        StudentPointAdjustmentRequest first = adjustmentTransaction.createWorkflow(
                command("first-replacement", 42L, 7, "first", admin, oldRequest.getId())).request();

        assertCode("POINT_ADJUSTMENT_ALREADY_REPLACED", () -> adjustmentTransaction.createWorkflow(
                command("duplicate-replacement", 42L, 9, "second", admin, oldRequest.getId())));

        assertEquals(PointEventStatus.CANCELLED, eventRepository.findById(oldEvent.getId()).orElseThrow().getStatus());
        assertEquals(2, requestRepository.count());
    }

    @Test
    void teacherCannotReplaceAnotherTeachersAdjustment() {
        AppUser originalTeacher = actor(7L, UserRole.TEACHER);
        AppUser otherTeacher = actor(8L, UserRole.TEACHER);
        StudentPointAdjustmentRequest oldRequest = requestRepository.saveAndFlush(
                request(42L, 5, originalTeacher, PointAdjustmentStatus.FAILED));
        eventRepository.saveAndFlush(event(oldRequest, PointEventStatus.FAILED, 3));
        assertCode("POINT_ADJUSTMENT_NOT_OWNED", () -> adjustmentTransaction.createWorkflow(
                command("other-teacher", 42L, 6, "replace", otherTeacher, oldRequest.getId())));
        assertEquals(1, requestRepository.count());
    }

    @Test
    void replacementRejectsCorruptOldRequestEventLinkage() {
        AppUser admin = actor(1L, UserRole.ADMIN);
        StudentPointAdjustmentRequest oldRequest = requestRepository.saveAndFlush(
                request(42L, 5, admin, PointAdjustmentStatus.FAILED));
        StudentPointEvent oldEvent = event(oldRequest, PointEventStatus.FAILED, 3);
        oldEvent.setPoints(99);
        eventRepository.saveAndFlush(oldEvent);
        assertCode("MANUAL_ADJUSTMENT_STATE_INVALID", () -> adjustmentTransaction.createWorkflow(
                command("corrupt-replacement", 42L, 6, "replace", admin, oldRequest.getId())));
        assertEquals(1, requestRepository.count());
    }

    @Test
    void replacementAuditReasonPreservesPrefixAndTruncatesExactBoundaryReason() {
        AppUser admin = actor(1L, UserRole.ADMIN);
        StudentPointAdjustmentRequest oldRequest = requestRepository.saveAndFlush(
                request(42L, 5, admin, PointAdjustmentStatus.FAILED));
        StudentPointEvent oldEvent = eventRepository.saveAndFlush(
                event(oldRequest, PointEventStatus.FAILED, 3));
        String userReason = "x".repeat(500);
        StudentPointAdjustmentRequest replacement = adjustmentTransaction.createWorkflow(
                command("boundary-replacement", 42L, 6, userReason, admin, oldRequest.getId())).request();

        StudentPointEvent reloaded = eventRepository.findById(oldEvent.getId()).orElseThrow();
        String expectedPrefix = "Replaced by adjustment " + replacement.getId() + " by ADMIN#1: ";
        assertEquals(500, reloaded.getReason().length());
        assertEquals(500, reloaded.getProcessingReason().length());
        assertEquals(reloaded.getReason(), reloaded.getProcessingReason());
        assertEquals(expectedPrefix, reloaded.getReason().substring(0, expectedPrefix.length()));
        assertEquals(userReason.substring(0, 500 - expectedPrefix.length()),
                reloaded.getReason().substring(expectedPrefix.length()));
    }

    @Test
    void adminCancellationRejectsManualRequestInSameTransaction() {
        AppUser teacher = actor(7L, UserRole.TEACHER);
        AppUser admin = actor(1L, UserRole.ADMIN);
        StudentPointAdjustmentRequest request = requestRepository.saveAndFlush(
                request(42L, 5, teacher, PointAdjustmentStatus.FAILED));
        StudentPointEvent event = eventRepository.saveAndFlush(event(request, PointEventStatus.FAILED, 3));

        adminTransaction.cancelEvent(event.getId(), admin, "invalid request");

        assertEquals(PointEventStatus.CANCELLED, eventRepository.findById(event.getId()).orElseThrow().getStatus());
        assertEquals(PointAdjustmentStatus.REJECTED,
                requestRepository.findById(request.getId()).orElseThrow().getStatus());
    }

    @Test
    void adminCancellationRollsBackEventWhenManualRequestDoesNotMatch() {
        AppUser teacher = actor(7L, UserRole.TEACHER);
        AppUser admin = actor(1L, UserRole.ADMIN);
        StudentPointAdjustmentRequest request = requestRepository.saveAndFlush(
                request(42L, 5, teacher, PointAdjustmentStatus.PENDING));
        StudentPointEvent event = event(request, PointEventStatus.PENDING, 0);
        event.setPoints(99);
        event = eventRepository.saveAndFlush(event);
        Long eventId = event.getId();

        assertCode("MANUAL_ADJUSTMENT_STATE_INVALID", () ->
                adminTransaction.cancelEvent(eventId, admin, "invalid request"));

        assertEquals(PointEventStatus.PENDING, eventRepository.findById(eventId).orElseThrow().getStatus());
        assertEquals(PointAdjustmentStatus.PENDING,
                requestRepository.findById(request.getId()).orElseThrow().getStatus());
    }

    @Test
    void adminCancellationStateMatrixIsExplicit() {
        AppUser admin = actor(1L, UserRole.ADMIN);
        StudentPointEvent pending = eventRepository.saveAndFlush(ordinaryEvent(PointEventStatus.PENDING, "pending"));
        StudentPointEvent failed = eventRepository.saveAndFlush(ordinaryEvent(PointEventStatus.FAILED, "failed"));
        StudentPointEvent processing = eventRepository.saveAndFlush(ordinaryEvent(PointEventStatus.PROCESSING, "p"));
        StudentPointEvent succeeded = eventRepository.saveAndFlush(ordinaryEvent(PointEventStatus.SUCCEEDED, "s"));
        StudentPointEvent cancelled = eventRepository.saveAndFlush(ordinaryEvent(PointEventStatus.CANCELLED, "c"));

        StudentPointEvent cancelledPending = adminTransaction.cancelEvent(pending.getId(), admin, "cancel pending");
        StudentPointEvent cancelledFailed = adminTransaction.cancelEvent(failed.getId(), admin, "cancel failed");
        assertEquals(PointEventStatus.CANCELLED, cancelledPending.getStatus());
        assertEquals(PointEventStatus.CANCELLED, cancelledFailed.getStatus());
        assertEquals(1L, cancelledPending.getProcessingOperatorId());
        assertEquals("ADMIN", cancelledPending.getProcessingOperatorRole());
        assertEquals("cancel pending", cancelledPending.getProcessingReason());
        assertCode("POINT_EVENT_PROCESSING", () ->
                adminTransaction.cancelEvent(processing.getId(), admin, "cancel"));
        assertCode("POINT_EVENT_REVERSAL_REQUIRED", () ->
                adminTransaction.cancelEvent(succeeded.getId(), admin, "cancel"));
        assertEquals(cancelled.getId(), adminTransaction.cancelEvent(cancelled.getId(), admin, "again").getId());
    }

    private void assertReplacementCode(
            AppUser actor,
            PointEventStatus eventStatus,
            int attemptCount,
            PointAdjustmentStatus requestStatus,
            String expectedCode
    ) {
        StudentPointAdjustmentRequest oldRequest = requestRepository.saveAndFlush(
                request(42L, 5, actor, requestStatus));
        eventRepository.saveAndFlush(event(oldRequest, eventStatus, attemptCount));
        assertCode(expectedCode, () -> adjustmentTransaction.createWorkflow(
                command("blocked-" + expectedCode, 42L, 6, "replace", actor, oldRequest.getId())));
        assertEquals(1, requestRepository.count());
    }

    private AppUser actor(Long id, UserRole role) {
        AppUser actor = new AppUser();
        actor.setId(id);
        actor.setRole(role);
        return actor;
    }

    private StudentPointAdjustmentRequest request(
            Long studentId,
            int amount,
            AppUser actor,
            PointAdjustmentStatus status
    ) {
        StudentPointAdjustmentRequest request = StudentPointAdjustmentRequest.create(
                "legacy-test-" + REQUEST_SEQUENCE.incrementAndGet(),
                studentId,
                amount,
                "original",
                actor.getId(),
                actor.getRole().name(),
                null
        );
        request.setStatus(status);
        return request;
    }

    private StudentPointAdjustmentTransaction.CreateCommand command(
            String requestKey,
            Long studentId,
            int amount,
            String reason,
            AppUser actor,
            Long replacesRequestId
    ) {
        return new StudentPointAdjustmentTransaction.CreateCommand(
                requestKey, studentId, amount, reason, actor, replacesRequestId);
    }

    private StudentPointEvent event(
            StudentPointAdjustmentRequest request,
            PointEventStatus status,
            int attemptCount
    ) {
        StudentPointEvent event = new StudentPointEvent();
        event.setStudentId(request.getStudentId());
        event.setSourceType(PointSourceType.MANUAL_ADJUSTMENT);
        event.setSourceId(request.getId());
        event.setSourceKey("manual-adjustment:" + request.getId());
        event.setRuleCode("MANUAL_ADJUSTMENT");
        event.setRuleName("Manual adjustment");
        event.setPoints(request.getAmount());
        event.setIdempotencyKey(event.getSourceKey() + ":MANUAL_ADJUSTMENT");
        event.setStatus(status);
        event.setAutoAttemptCount(attemptCount);
        event.setOperatorId(request.getRequestedBy());
        event.setOperatorRole(request.getRequestedRole());
        event.setReason(request.getReason());
        return event;
    }

    private StudentPointEvent ordinaryEvent(PointEventStatus status, String key) {
        StudentPointEvent event = new StudentPointEvent();
        event.setStudentId(42L);
        event.setSourceType(PointSourceType.STUDY_RECORD);
        event.setSourceId(90L);
        event.setSourceKey("study:" + key);
        event.setRuleCode("STUDY_RECORD_CORRECT");
        event.setRuleName("Study");
        event.setPoints(1);
        event.setIdempotencyKey("study:" + key + ":STUDY_RECORD_CORRECT");
        event.setStatus(status);
        event.setAutoAttemptCount(0);
        return event;
    }

    private void assertCode(String code, Runnable operation) {
        StudentPointOperationException failure = assertThrows(StudentPointOperationException.class, operation::run);
        assertEquals(code, failure.getCode());
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
