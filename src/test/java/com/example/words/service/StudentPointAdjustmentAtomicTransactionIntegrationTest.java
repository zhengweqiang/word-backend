package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;

import com.example.words.model.AppUser;
import com.example.words.model.PointAdjustmentStatus;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointAdjustmentRequest;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.UserRole;
import com.example.words.repository.StudentPointAdjustmentRequestRepository;
import com.example.words.repository.StudentPointEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        StudentPointAdjustmentTransaction.class,
        StudentPointEventFactory.class,
        StudentPointAdjustmentAtomicTransactionIntegrationTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentPointAdjustmentAtomicTransactionIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-22T06:00:00Z");

    @Autowired
    private StudentPointAdjustmentTransaction transaction;

    @SpyBean
    private StudentPointAdjustmentRequestRepository requestRepository;

    @SpyBean
    private StudentPointEventRepository eventRepository;

    @BeforeEach
    void clean() {
        eventRepository.deleteAll();
        requestRepository.deleteAll();
        clearInvocations(eventRepository, requestRepository);
    }

    @Test
    void initialRequestAndManualEventCommitTogether() {
        StudentPointAdjustmentTransaction.Workflow workflow = transaction.createWorkflow(
                command("initial-key", 42L, 9, "class reward", admin(), null));

        StudentPointAdjustmentRequest request = requestRepository.findById(workflow.request().getId()).orElseThrow();
        StudentPointEvent event = eventRepository.findById(workflow.event().getId()).orElseThrow();
        assertEquals("initial-key", request.getRequestKey());
        assertEquals(PointAdjustmentStatus.PENDING, request.getStatus());
        assertEquals(request.getId(), event.getSourceId());
        assertEquals("manual-adjustment:" + request.getId(), event.getSourceKey());
        assertEquals(event.getSourceKey() + ":MANUAL_ADJUSTMENT", event.getIdempotencyKey());
        assertEquals(PointEventStatus.PENDING, event.getStatus());
    }

    @Test
    void initialEventInsertFailureRollsBackRequest() {
        doThrow(new DataIntegrityViolationException("event insert failed"))
                .when(eventRepository)
                .saveAndFlush(argThat(event -> event.getSourceType() == PointSourceType.MANUAL_ADJUSTMENT));

        assertThrows(DataIntegrityViolationException.class, () -> transaction.createWorkflow(
                command("rollback-initial", 42L, 9, "class reward", admin(), null)));

        assertEquals(0, requestRepository.count());
        assertEquals(0, eventRepository.count());
    }

    @Test
    void replacementLocksEventThenOldRequestAndCommitsStructuredAudit() {
        StudentPointAdjustmentRequest oldRequest = failedRequest("old-key", 42L, 5);
        StudentPointEvent oldEvent = failedEvent(oldRequest, 3);
        clearInvocations(eventRepository, requestRepository);

        StudentPointAdjustmentTransaction.Workflow workflow = transaction.createWorkflow(
                command("replacement-key", 42L, 8, "corrected", admin(), oldRequest.getId()));

        InOrder order = inOrder(eventRepository, requestRepository);
        order.verify(eventRepository).findBySourceTypeAndSourceIdForUpdate(
                PointSourceType.MANUAL_ADJUSTMENT, oldRequest.getId());
        order.verify(requestRepository).findByIdForUpdate(oldRequest.getId());

        StudentPointAdjustmentRequest reloadedOld = requestRepository.findById(oldRequest.getId()).orElseThrow();
        StudentPointAdjustmentRequest reloadedNew = requestRepository.findById(workflow.request().getId()).orElseThrow();
        StudentPointEvent reloadedOldEvent = eventRepository.findById(oldEvent.getId()).orElseThrow();
        assertEquals(PointAdjustmentStatus.REJECTED, reloadedOld.getStatus());
        assertEquals(reloadedNew.getId(), reloadedOld.getReplacedByRequestId());
        assertEquals(reloadedOld.getId(), reloadedNew.getReplacesRequestId());
        assertEquals(PointEventStatus.CANCELLED, reloadedOldEvent.getStatus());
        assertEquals("Replaced by adjustment " + reloadedNew.getId() + " by ADMIN#1: corrected",
                reloadedOldEvent.getReason());
    }

    @Test
    void replacementEventInsertFailureRollsBackOldCloseAndNewRequest() {
        StudentPointAdjustmentRequest oldRequest = failedRequest("old-rollback-key", 42L, 5);
        StudentPointEvent oldEvent = failedEvent(oldRequest, 3);
        clearInvocations(eventRepository, requestRepository);
        doThrow(new DataIntegrityViolationException("new event insert failed"))
                .when(eventRepository)
                .saveAndFlush(argThat(event -> event.getId() == null
                        && event.getSourceType() == PointSourceType.MANUAL_ADJUSTMENT));

        assertThrows(DataIntegrityViolationException.class, () -> transaction.createWorkflow(
                command("replacement-rollback", 42L, 8, "corrected", admin(), oldRequest.getId())));

        StudentPointAdjustmentRequest reloadedOld = requestRepository.findById(oldRequest.getId()).orElseThrow();
        StudentPointEvent reloadedOldEvent = eventRepository.findById(oldEvent.getId()).orElseThrow();
        assertEquals(PointAdjustmentStatus.FAILED, reloadedOld.getStatus());
        assertNull(reloadedOld.getReplacedByRequestId());
        assertEquals(PointEventStatus.FAILED, reloadedOldEvent.getStatus());
        assertEquals(1, requestRepository.count());
        assertEquals(1, eventRepository.count());
    }

    private StudentPointAdjustmentRequest failedRequest(String key, Long studentId, int amount) {
        StudentPointAdjustmentRequest request = StudentPointAdjustmentRequest.create(
                key, studentId, amount, "original", 1L, "ADMIN", null);
        request.setStatus(PointAdjustmentStatus.FAILED);
        return requestRepository.saveAndFlush(request);
    }

    private StudentPointEvent failedEvent(StudentPointAdjustmentRequest request, int autoAttemptCount) {
        StudentPointEvent event = new StudentPointEvent();
        event.setStudentId(request.getStudentId());
        event.setSourceType(PointSourceType.MANUAL_ADJUSTMENT);
        event.setSourceId(request.getId());
        event.setSourceKey("manual-adjustment:" + request.getId());
        event.setRuleCode("MANUAL_ADJUSTMENT");
        event.setRuleName("Manual adjustment");
        event.setPoints(request.getAmount());
        event.setIdempotencyKey(event.getSourceKey() + ":MANUAL_ADJUSTMENT");
        event.setStatus(PointEventStatus.FAILED);
        event.setAutoAttemptCount(autoAttemptCount);
        event.setOperatorId(request.getRequestedBy());
        event.setOperatorRole(request.getRequestedRole());
        event.setReason(request.getReason());
        return eventRepository.saveAndFlush(event);
    }

    private StudentPointAdjustmentTransaction.CreateCommand command(
            String key,
            Long studentId,
            int amount,
            String reason,
            AppUser actor,
            Long replacesRequestId
    ) {
        return new StudentPointAdjustmentTransaction.CreateCommand(
                key, studentId, amount, reason, actor, replacesRequestId);
    }

    private AppUser admin() {
        AppUser actor = new AppUser();
        actor.setId(1L);
        actor.setRole(UserRole.ADMIN);
        return actor;
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
