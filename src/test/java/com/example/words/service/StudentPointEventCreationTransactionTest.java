package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.StudentPointRule;
import com.example.words.repository.StudentPointEventRepository;
import com.example.words.repository.StudentPointRuleRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class StudentPointEventCreationTransactionTest {

    @Mock
    private StudentPointRuleRepository ruleRepository;

    @Mock
    private StudentPointEventRepository eventRepository;

    private StudentPointEventCreationTransaction creationTransaction;

    @BeforeEach
    void setUp() {
        creationTransaction = new StudentPointEventCreationTransaction(
                ruleRepository, eventRepository, new StudentPointEventFactory());
    }

    @Test
    void createRuleEventShouldLockRuleAndPersistImmutableSnapshot() {
        stubEventSave();
        StudentPointRule rule = StudentPointRule.create(
                "STUDY_RECORD_CORRECT", " 答对单词 ", PointSourceType.STUDY_RECORD, 1);
        when(ruleRepository.findByCodeForUpdate("STUDY_RECORD_CORRECT")).thenReturn(Optional.of(rule));

        StudentPointEvent event = creationTransaction.createRuleEvent(ruleRequest());

        assertEquals(42L, event.getStudentId());
        assertEquals(PointSourceType.STUDY_RECORD, event.getSourceType());
        assertEquals(88L, event.getSourceId());
        assertEquals("record:88", event.getSourceKey());
        assertEquals("STUDY_RECORD_CORRECT", event.getRuleCode());
        assertEquals("答对单词", event.getRuleName());
        assertEquals(1, event.getPoints());
        assertEquals("record:88:STUDY_RECORD_CORRECT", event.getIdempotencyKey());
        assertEquals(PointEventStatus.PENDING, event.getStatus());
        assertEquals(0, event.getAutoAttemptCount());
        assertEquals(9L, event.getOperatorId());
        assertEquals("SYSTEM", event.getOperatorRole());
        assertEquals("答对单词", event.getReason());
        verify(ruleRepository).findByCodeForUpdate("STUDY_RECORD_CORRECT");
    }

    @Test
    void eventSnapshotShouldNotFollowLaterRuleChanges() {
        stubEventSave();
        StudentPointRule rule = StudentPointRule.create(
                "STUDY_RECORD_CORRECT", "答对单词", PointSourceType.STUDY_RECORD, 1);
        when(ruleRepository.findByCodeForUpdate("STUDY_RECORD_CORRECT")).thenReturn(Optional.of(rule));

        StudentPointEvent event = creationTransaction.createRuleEvent(ruleRequest());
        rule.setName("新名称");
        rule.setSourceType(PointSourceType.EXAM);
        rule.setBasePoints(99);

        assertEquals("答对单词", event.getRuleName());
        assertEquals(PointSourceType.STUDY_RECORD, event.getSourceType());
        assertEquals(1, event.getPoints());
    }

    @Test
    void createRuleEventShouldRejectMissingDisabledAndInvalidRule() {
        when(ruleRepository.findByCodeForUpdate("MISSING")).thenReturn(Optional.empty());
        assertFailure("POINT_RULE_NOT_FOUND", HttpStatus.NOT_FOUND,
                () -> creationTransaction.createRuleEvent(ruleRequest("MISSING")));

        StudentPointRule disabled = StudentPointRule.create(
                "DISABLED", "停用规则", PointSourceType.STUDY_TASK, 1);
        disabled.setEnabled(false);
        when(ruleRepository.findByCodeForUpdate("DISABLED")).thenReturn(Optional.of(disabled));
        assertFailure("POINT_RULE_DISABLED", HttpStatus.CONFLICT,
                () -> creationTransaction.createRuleEvent(ruleRequest("DISABLED")));

        StudentPointRule zero = StudentPointRule.create("ZERO", "零积分", PointSourceType.STUDY_TASK, 0);
        when(ruleRepository.findByCodeForUpdate("ZERO")).thenReturn(Optional.of(zero));
        assertFailure("POINT_RULE_CONFIGURATION_INVALID", HttpStatus.INTERNAL_SERVER_ERROR,
                () -> creationTransaction.createRuleEvent(ruleRequest("ZERO")));

        StudentPointRule missingPoints = StudentPointRule.create(
                "NULL_POINTS", "缺少积分", PointSourceType.STUDY_TASK, null);
        when(ruleRepository.findByCodeForUpdate("NULL_POINTS")).thenReturn(Optional.of(missingPoints));
        assertFailure("POINT_RULE_CONFIGURATION_INVALID", HttpStatus.INTERNAL_SERVER_ERROR,
                () -> creationTransaction.createRuleEvent(ruleRequest("NULL_POINTS")));
    }

    @Test
    void createManualEventShouldPersistFixedManualSnapshot() {
        stubEventSave();
        StudentPointEventCreationTransaction.ManualEventRequest request =
                new StudentPointEventCreationTransaction.ManualEventRequest(
                        42L,
                        77L,
                        -5,
                        "manual-adjustment:77",
                        "manual-adjustment:77:MANUAL_ADJUSTMENT",
                        1L,
                        "ADMIN",
                        "纠正误发积分"
                );

        StudentPointEvent event = creationTransaction.createManualEvent(request);

        assertEquals(PointSourceType.MANUAL_ADJUSTMENT, event.getSourceType());
        assertEquals(77L, event.getSourceId());
        assertEquals("MANUAL_ADJUSTMENT", event.getRuleCode());
        assertEquals("人工积分调整", event.getRuleName());
        assertEquals(-5, event.getPoints());
        assertEquals(1L, event.getOperatorId());
        assertEquals("ADMIN", event.getOperatorRole());
        assertEquals("纠正误发积分", event.getReason());
        ArgumentCaptor<StudentPointEvent> captor = ArgumentCaptor.forClass(StudentPointEvent.class);
        verify(eventRepository).saveAndFlush(captor.capture());
        assertSame(event, captor.getValue());
    }

    private void stubEventSave() {
        when(eventRepository.saveAndFlush(any(StudentPointEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private StudentPointEventCreationTransaction.RuleEventRequest ruleRequest() {
        return ruleRequest("STUDY_RECORD_CORRECT");
    }

    private StudentPointEventCreationTransaction.RuleEventRequest ruleRequest(String ruleCode) {
        return new StudentPointEventCreationTransaction.RuleEventRequest(
                42L,
                88L,
                "record:88",
                ruleCode,
                "record:88:" + ruleCode,
                9L,
                "SYSTEM",
                "答对单词"
        );
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
}
