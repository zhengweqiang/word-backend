package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointEvent;
import com.example.words.repository.StudentPointEventRepository;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class StudentPointEventServiceTest {

    private static final StudentPointEventService.Actor SYSTEM_ACTOR =
            new StudentPointEventService.Actor(9L, " SYSTEM ");

    @Mock
    private StudentPointEventRepository eventRepository;

    @Mock
    private StudentPointEventCreationTransaction creationTransaction;

    private StudentPointEventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new StudentPointEventService(
                eventRepository, creationTransaction, new StudentPointEventFactory(), Clock.systemUTC());
    }

    @Test
    void newRuleEventShouldDelegateNormalizedIdentityToCreationTransaction() {
        when(eventRepository.findByIdempotencyKey("record:88:STUDY_RECORD_CORRECT"))
                .thenReturn(Optional.empty());
        StudentPointEvent created = matchingRuleEvent(PointEventStatus.PENDING);
        when(creationTransaction.createRuleEvent(any())).thenReturn(created);

        StudentPointEvent result = eventService.create(new StudentPointEventService.CreateRequest(
                42L, 88L, " record:88 ", " STUDY_RECORD_CORRECT ", SYSTEM_ACTOR, " 答对单词 "
        ));

        assertSame(created, result);
        ArgumentCaptor<StudentPointEventCreationTransaction.RuleEventRequest> captor =
                ArgumentCaptor.forClass(StudentPointEventCreationTransaction.RuleEventRequest.class);
        verify(creationTransaction).createRuleEvent(captor.capture());
        StudentPointEventCreationTransaction.RuleEventRequest request = captor.getValue();
        assertEquals(42L, request.studentId());
        assertEquals(88L, request.sourceId());
        assertEquals("record:88", request.sourceKey());
        assertEquals("STUDY_RECORD_CORRECT", request.ruleCode());
        assertEquals("record:88:STUDY_RECORD_CORRECT", request.idempotencyKey());
        assertEquals(9L, request.operatorId());
        assertEquals("SYSTEM", request.operatorRole());
        assertEquals("答对单词", request.reason());
    }

    @Test
    void existingRuleEventShouldReplayWithoutLoadingCurrentRuleOrCreating() {
        StudentPointEvent existing = matchingRuleEvent(PointEventStatus.SUCCEEDED);
        when(eventRepository.findByIdempotencyKey("record:88:STUDY_RECORD_CORRECT"))
                .thenReturn(Optional.of(existing));

        StudentPointEvent replayed = eventService.create(ruleRequest());

        assertSame(existing, replayed);
        verify(creationTransaction, never()).createRuleEvent(any());
    }

    @Test
    void existingRuleEventSnapshotRemainsAuthoritativeAfterRuleChangeDisableOrDeletion() {
        for (PointEventStatus status : PointEventStatus.values()) {
            StudentPointEvent existing = matchingRuleEvent(status);
            existing.setRuleName("旧规则名称");
            existing.setSourceType(PointSourceType.STUDY_RECORD);
            existing.setPoints(1);
            when(eventRepository.findByIdempotencyKey("record:88:STUDY_RECORD_CORRECT"))
                    .thenReturn(Optional.of(existing));

            StudentPointEvent replayed = eventService.create(ruleRequest());

            assertSame(existing, replayed);
            assertEquals("旧规则名称", replayed.getRuleName());
            assertEquals(1, replayed.getPoints());
        }
        verify(creationTransaction, never()).createRuleEvent(any());
    }

    @Test
    void existingRuleEventShouldValidateOnlyCallerIdentity() {
        StudentPointEvent existing = matchingRuleEvent(PointEventStatus.CANCELLED);
        existing.setSourceType(PointSourceType.EXAM);
        existing.setRuleName("历史快照");
        existing.setPoints(999);
        existing.setOperatorId(77L);
        existing.setOperatorRole("TEACHER");
        existing.setReason("历史原因");
        when(eventRepository.findByIdempotencyKey("record:88:STUDY_RECORD_CORRECT"))
                .thenReturn(Optional.of(existing));

        assertSame(existing, eventService.create(ruleRequest()));
        verify(creationTransaction, never()).createRuleEvent(any());
    }

    @Test
    void existingRuleEventWithDifferentCallerIdentityShouldConflict() {
        assertRuleConflict(ruleEvent(99L, 88L, "record:88", "STUDY_RECORD_CORRECT"));
        assertRuleConflict(ruleEvent(42L, 99L, "record:88", "STUDY_RECORD_CORRECT"));
        assertRuleConflict(ruleEvent(42L, 88L, "other", "STUDY_RECORD_CORRECT"));
        assertRuleConflict(ruleEvent(42L, 88L, "record:88", "OTHER"));

        StudentPointEvent wrongKey = ruleEvent(42L, 88L, "record:88", "STUDY_RECORD_CORRECT");
        wrongKey.setIdempotencyKey("different-key");
        assertRuleConflict(wrongKey);
    }

    @Test
    void namedUniqueRaceShouldRecoverMatchingEventOutsideFailedCreationTransaction() {
        StudentPointEvent winner = matchingRuleEvent(PointEventStatus.PROCESSING);
        when(eventRepository.findByIdempotencyKey("record:88:STUDY_RECORD_CORRECT"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(creationTransaction.createRuleEvent(any())).thenThrow(namedUniqueFailure());

        assertSame(winner, eventService.create(ruleRequest()));
    }

    @Test
    void namedUniqueRaceWithDifferentIdentityShouldConflict() {
        StudentPointEvent winner = ruleEvent(99L, 88L, "record:88", "STUDY_RECORD_CORRECT");
        when(eventRepository.findByIdempotencyKey("record:88:STUDY_RECORD_CORRECT"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(creationTransaction.createRuleEvent(any())).thenThrow(namedUniqueFailure());

        assertFailure("IDEMPOTENCY_KEY_CONFLICT", HttpStatus.CONFLICT,
                () -> eventService.create(ruleRequest()));
    }

    @Test
    void unrelatedIntegrityFailureShouldPropagate() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "check constraint failed",
                new IllegalStateException("ck_student_point_events_points")
        );
        when(eventRepository.findByIdempotencyKey("record:88:STUDY_RECORD_CORRECT"))
                .thenReturn(Optional.empty());
        when(creationTransaction.createRuleEvent(any())).thenThrow(failure);

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> eventService.create(ruleRequest())));
        verify(eventRepository).findByIdempotencyKey("record:88:STUDY_RECORD_CORRECT");
    }

    @Test
    void ordinaryRequestShouldValidateRequiredValuesAndLengthBoundaries() {
        assertInvalid(() -> eventService.create(ruleRequest(0L, "source", "RULE", null, null)));
        assertInvalid(() -> eventService.create(ruleRequest(42L, " ", "RULE", null, null)));
        assertInvalid(() -> eventService.create(ruleRequest(42L, "source", " ", null, null)));
        assertInvalid(() -> eventService.create(null));

        assertTooLong(() -> eventService.create(ruleRequest(42L, repeat('s', 201), "R", null, null)));
        assertTooLong(() -> eventService.create(ruleRequest(42L, "s", repeat('r', 65), null, null)));
        assertTooLong(() -> eventService.create(ruleRequest(
                42L, repeat('s', 150), repeat('r', 10), null, null)));
        assertTooLong(() -> eventService.create(ruleRequest(
                42L, "s", "R", new StudentPointEventService.Actor(1L, repeat('A', 33)), null)));
        assertTooLong(() -> eventService.create(ruleRequest(
                42L, "s", "R", null, repeat('x', 501))));
        verify(creationTransaction, never()).createRuleEvent(any());
    }

    @Test
    void ordinaryRequestShouldAcceptExactLengthBoundaries() {
        String sourceKey = repeat('s', 95);
        String ruleCode = repeat('r', 64);
        String idempotencyKey = sourceKey + ":" + ruleCode;
        when(eventRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        StudentPointEvent created = ruleEvent(42L, 88L, sourceKey, ruleCode);
        when(creationTransaction.createRuleEvent(any())).thenReturn(created);

        assertSame(created, eventService.create(ruleRequest(
                42L,
                sourceKey,
                ruleCode,
                new StudentPointEventService.Actor(1L, repeat('A', 32)),
                repeat('x', 500)
        )));
    }

    @Test
    void newManualAdjustmentShouldDelegateNormalizedFixedFields() {
        when(eventRepository.findByIdempotencyKey("manual-adjustment:77:MANUAL_ADJUSTMENT"))
                .thenReturn(Optional.empty());
        StudentPointEvent created = manualEvent(42L, 77L, -5, 1L, "ADMIN", "纠正误发积分");
        when(creationTransaction.createManualEvent(any())).thenReturn(created);

        StudentPointEvent result = eventService.createManualAdjustment(
                42L, 77L, -5, new StudentPointEventService.Actor(1L, " ADMIN "), " 纠正误发积分 "
        );

        assertSame(created, result);
        ArgumentCaptor<StudentPointEventCreationTransaction.ManualEventRequest> captor =
                ArgumentCaptor.forClass(StudentPointEventCreationTransaction.ManualEventRequest.class);
        verify(creationTransaction).createManualEvent(captor.capture());
        StudentPointEventCreationTransaction.ManualEventRequest request = captor.getValue();
        assertEquals(42L, request.studentId());
        assertEquals(77L, request.adjustmentRequestId());
        assertEquals(-5, request.amount());
        assertEquals("manual-adjustment:77", request.sourceKey());
        assertEquals("manual-adjustment:77:MANUAL_ADJUSTMENT", request.idempotencyKey());
        assertEquals(1L, request.operatorId());
        assertEquals("ADMIN", request.operatorRole());
        assertEquals("纠正误发积分", request.reason());
    }

    @Test
    void manualDuplicateShouldRequireFullManualFingerprint() {
        StudentPointEvent existing = manualEvent(42L, 77L, 5, 1L, "ADMIN", "补发积分");
        when(eventRepository.findByIdempotencyKey("manual-adjustment:77:MANUAL_ADJUSTMENT"))
                .thenReturn(Optional.of(existing));

        assertSame(existing, eventService.createManualAdjustment(
                42L, 77L, 5, new StudentPointEventService.Actor(1L, " ADMIN "), " 补发积分 "));

        assertManualConflict(99L, 77L, 5, 1L, "ADMIN", "补发积分", existing);
        assertManualConflict(42L, 77L, 6, 1L, "ADMIN", "补发积分", existing);
        assertManualConflict(42L, 77L, 5, 2L, "ADMIN", "补发积分", existing);
        assertManualConflict(42L, 77L, 5, 1L, "TEACHER", "补发积分", existing);
        assertManualConflict(42L, 77L, 5, 1L, "ADMIN", "不同原因", existing);

        StudentPointEvent wrongType = manualEvent(42L, 77L, 5, 1L, "ADMIN", "补发积分");
        wrongType.setSourceType(PointSourceType.ADMIN_CORRECTION);
        assertManualConflict(42L, 77L, 5, 1L, "ADMIN", "补发积分", wrongType);

        StudentPointEvent wrongRuleName = manualEvent(42L, 77L, 5, 1L, "ADMIN", "补发积分");
        wrongRuleName.setRuleName("其他人工规则");
        assertManualConflict(42L, 77L, 5, 1L, "ADMIN", "补发积分", wrongRuleName);
        verify(creationTransaction, never()).createManualEvent(any());
    }

    @Test
    void manualRequestShouldValidateValuesAndLengths() {
        assertInvalid(() -> eventService.createManualAdjustment(
                42L, 77L, 0, new StudentPointEventService.Actor(1L, "ADMIN"), "原因"));
        assertInvalid(() -> eventService.createManualAdjustment(
                42L, null, 1, new StudentPointEventService.Actor(1L, "ADMIN"), "原因"));
        assertInvalid(() -> eventService.createManualAdjustment(42L, 77L, 1, null, "原因"));
        assertInvalid(() -> eventService.createManualAdjustment(
                42L, 77L, 1, new StudentPointEventService.Actor(1L, " "), "原因"));
        assertInvalid(() -> eventService.createManualAdjustment(
                42L, 77L, 1, new StudentPointEventService.Actor(1L, "ADMIN"), " "));
        assertTooLong(() -> eventService.createManualAdjustment(
                42L, 77L, 1, new StudentPointEventService.Actor(1L, repeat('A', 33)), "原因"));
        assertTooLong(() -> eventService.createManualAdjustment(
                42L, 77L, 1, new StudentPointEventService.Actor(1L, "ADMIN"), repeat('x', 501)));
    }

    @Test
    void namedUniqueRaceShouldRecoverMatchingManualEvent() {
        StudentPointEvent winner = manualEvent(42L, 77L, 5, 1L, "ADMIN", "原因");
        when(eventRepository.findByIdempotencyKey("manual-adjustment:77:MANUAL_ADJUSTMENT"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(creationTransaction.createManualEvent(any())).thenThrow(namedUniqueFailure());

        assertSame(winner, eventService.createManualAdjustment(
                42L, 77L, 5, new StudentPointEventService.Actor(1L, "ADMIN"), "原因"));
    }

    private StudentPointEventService.CreateRequest ruleRequest() {
        return ruleRequest(42L, "record:88", "STUDY_RECORD_CORRECT", SYSTEM_ACTOR, "答对单词");
    }

    private StudentPointEventService.CreateRequest ruleRequest(
            Long studentId,
            String sourceKey,
            String ruleCode,
            StudentPointEventService.Actor actor,
            String reason
    ) {
        return new StudentPointEventService.CreateRequest(studentId, 88L, sourceKey, ruleCode, actor, reason);
    }

    private StudentPointEvent matchingRuleEvent(PointEventStatus status) {
        StudentPointEvent event = ruleEvent(42L, 88L, "record:88", "STUDY_RECORD_CORRECT");
        event.setStatus(status);
        return event;
    }

    private StudentPointEvent ruleEvent(Long studentId, Long sourceId, String sourceKey, String ruleCode) {
        StudentPointEvent event = new StudentPointEvent();
        event.setStudentId(studentId);
        event.setSourceType(PointSourceType.STUDY_RECORD);
        event.setSourceId(sourceId);
        event.setSourceKey(sourceKey);
        event.setRuleCode(ruleCode);
        event.setRuleName("答对单词");
        event.setPoints(1);
        event.setIdempotencyKey("record:88:STUDY_RECORD_CORRECT");
        event.setStatus(PointEventStatus.PENDING);
        event.setAutoAttemptCount(0);
        return event;
    }

    private StudentPointEvent manualEvent(
            Long studentId,
            Long requestId,
            int amount,
            Long operatorId,
            String operatorRole,
            String reason
    ) {
        StudentPointEvent event = new StudentPointEvent();
        event.setStudentId(studentId);
        event.setSourceType(PointSourceType.MANUAL_ADJUSTMENT);
        event.setSourceId(requestId);
        event.setSourceKey("manual-adjustment:" + requestId);
        event.setRuleCode("MANUAL_ADJUSTMENT");
        event.setRuleName("人工积分调整");
        event.setPoints(amount);
        event.setIdempotencyKey("manual-adjustment:" + requestId + ":MANUAL_ADJUSTMENT");
        event.setStatus(PointEventStatus.PENDING);
        event.setAutoAttemptCount(0);
        event.setOperatorId(operatorId);
        event.setOperatorRole(operatorRole);
        event.setReason(reason);
        return event;
    }

    private void assertRuleConflict(StudentPointEvent existing) {
        when(eventRepository.findByIdempotencyKey("record:88:STUDY_RECORD_CORRECT"))
                .thenReturn(Optional.of(existing));
        assertFailure("IDEMPOTENCY_KEY_CONFLICT", HttpStatus.CONFLICT,
                () -> eventService.create(ruleRequest()));
    }

    private void assertManualConflict(
            Long studentId,
            Long requestId,
            int amount,
            Long operatorId,
            String operatorRole,
            String reason,
            StudentPointEvent existing
    ) {
        when(eventRepository.findByIdempotencyKey("manual-adjustment:77:MANUAL_ADJUSTMENT"))
                .thenReturn(Optional.of(existing));
        assertFailure("IDEMPOTENCY_KEY_CONFLICT", HttpStatus.CONFLICT,
                () -> eventService.createManualAdjustment(
                        studentId,
                        requestId,
                        amount,
                        new StudentPointEventService.Actor(operatorId, operatorRole),
                        reason
                ));
    }

    private DataIntegrityViolationException namedUniqueFailure() {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new IllegalStateException("duplicate key violates constraint uk_student_point_events_idempotency")
        );
    }

    private void assertInvalid(org.junit.jupiter.api.function.Executable action) {
        assertFailure("INVALID_POINT_EVENT_REQUEST", HttpStatus.BAD_REQUEST, action);
    }

    private void assertTooLong(org.junit.jupiter.api.function.Executable action) {
        assertFailure("POINT_EVENT_FIELD_TOO_LONG", HttpStatus.BAD_REQUEST, action);
    }

    private void assertFailure(
            String code,
            HttpStatus status,
            org.junit.jupiter.api.function.Executable action
    ) {
        StudentPointOperationException failure = assertThrows(StudentPointOperationException.class, action);
        assertEquals(code, failure.getCode());
        assertEquals(status, failure.getStatus());
    }

    private String repeat(char value, int count) {
        return String.valueOf(value).repeat(count);
    }
}
