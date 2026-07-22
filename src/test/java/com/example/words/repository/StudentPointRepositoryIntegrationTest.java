package com.example.words.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.words.model.PointEventAttemptStatus;
import com.example.words.model.PointAdjustmentStatus;
import com.example.words.model.PointAttemptTriggerType;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.PointTransactionType;
import com.example.words.model.StudentPointAccount;
import com.example.words.model.StudentPointAdjustmentRequest;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.StudentPointEventAttempt;
import com.example.words.model.StudentPointRule;
import com.example.words.model.StudentPointTransaction;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class StudentPointRepositoryIntegrationTest {

    @Autowired
    private StudentPointAccountRepository accountRepository;

    @Autowired
    private StudentPointTransactionRepository transactionRepository;

    @Autowired
    private StudentPointEventRepository eventRepository;

    @Autowired
    private StudentPointEventAttemptRepository eventAttemptRepository;

    @Autowired
    private StudentPointRuleRepository ruleRepository;

    @Autowired
    private StudentPointAdjustmentRequestRepository adjustmentRequestRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void enforcesOneAccountPerStudent() {
        accountRepository.saveAndFlush(StudentPointAccount.create(7L));

        assertThrows(DataIntegrityViolationException.class,
                () -> accountRepository.saveAndFlush(StudentPointAccount.create(7L)));
    }

    @Test
    void rejectsNegativeAvailableBalance() {
        StudentPointAccount account = StudentPointAccount.create(8L);
        account.setAvailablePoints(-1);

        assertThrows(DataIntegrityViolationException.class, () -> accountRepository.saveAndFlush(account));
    }

    @Test
    void enforcesUniqueTransactionIdempotencyKey() {
        transactionRepository.saveAndFlush(transaction("study-record:9:correct:STUDY_RECORD_CORRECT", 10L));

        assertThrows(DataIntegrityViolationException.class, () -> transactionRepository.saveAndFlush(
                transaction("study-record:9:correct:STUDY_RECORD_CORRECT", 10L)));
    }

    @Test
    void todayEarnedCountsOnlyUnreversedEarnTransactions() {
        LocalDateTime now = LocalDateTime.now();
        StudentPointTransaction reversedEarn = transaction("earn-reversed", 10L);
        reversedEarn.setAmount(10);
        reversedEarn.setBalanceAfter(10);
        reversedEarn.setCreatedAt(now);
        reversedEarn = transactionRepository.saveAndFlush(reversedEarn);

        StudentPointTransaction earn = transaction("earn-kept", 10L);
        earn.setAmount(3);
        earn.setBalanceBefore(10);
        earn.setBalanceAfter(13);
        earn.setCreatedAt(now);
        transactionRepository.saveAndFlush(earn);

        StudentPointTransaction reversal = transaction("reverse-earn", 10L);
        reversal.setTransactionType(PointTransactionType.REVERSE);
        reversal.setAmount(-10);
        reversal.setBalanceBefore(13);
        reversal.setBalanceAfter(3);
        reversal.setReversedTransactionId(reversedEarn.getId());
        reversal.setCreatedAt(now);
        transactionRepository.saveAndFlush(reversal);

        StudentPointTransaction reverseDeduction = transaction("reverse-deduction", 10L);
        reverseDeduction.setTransactionType(PointTransactionType.REVERSE);
        reverseDeduction.setAmount(5);
        reverseDeduction.setBalanceBefore(3);
        reverseDeduction.setBalanceAfter(8);
        reverseDeduction.setReversedTransactionId(999L);
        reverseDeduction.setCreatedAt(now);
        transactionRepository.saveAndFlush(reverseDeduction);

        assertEquals(3L, transactionRepository.sumEarnedByStudentIdBetween(
                10L, now.minusMinutes(1), now.plusMinutes(1)));
    }

    @Test
    void enforcesUniqueEventIdempotencyKeyAndPersistsProcessingMetadata() {
        StudentPointEvent event = event("study-record:9:correct:STUDY_RECORD_CORRECT", 11L);
        event.setProcessingTriggerType(PointAttemptTriggerType.MANUAL);
        event.setProcessingOperatorId(3L);
        event.setProcessingOperatorRole("ADMIN");
        event.setProcessingReason("retry after investigation");
        event.setProcessingStartedAt(LocalDateTime.of(2026, 7, 21, 10, 30));

        StudentPointEvent saved = eventRepository.saveAndFlush(event);

        StudentPointEvent reloaded = eventRepository.findById(saved.getId()).orElseThrow();
        assertEquals(PointAttemptTriggerType.MANUAL, reloaded.getProcessingTriggerType());
        assertEquals(3L, reloaded.getProcessingOperatorId());
        assertEquals("ADMIN", reloaded.getProcessingOperatorRole());
        assertEquals("retry after investigation", reloaded.getProcessingReason());
        assertEquals(LocalDateTime.of(2026, 7, 21, 10, 30), reloaded.getProcessingStartedAt());
        assertThrows(DataIntegrityViolationException.class,
                () -> eventRepository.saveAndFlush(event("study-record:9:correct:STUDY_RECORD_CORRECT", 11L)));
    }

    @Test
    void enforcesGloballyUniqueRuleCode() {
        ruleRepository.saveAndFlush(rule("STUDY_RECORD_CORRECT", "Correct answer"));

        assertThrows(DataIntegrityViolationException.class,
                () -> ruleRepository.saveAndFlush(rule("STUDY_RECORD_CORRECT", "Another name")));
    }

    @Test
    void ruleCodeCannotBeChangedThroughTheEntityApi() {
        assertThrows(NoSuchMethodException.class,
                () -> StudentPointRule.class.getMethod("setCode", String.class));
    }

    @Test
    void ruleCodeIsNotUpdatedAfterInsert() {
        StudentPointRule saved = ruleRepository.saveAndFlush(rule("STUDY_RECORD_CORRECT", "Correct answer"));
        ReflectionTestUtils.setField(saved, "code", "CHANGED_CODE");

        ruleRepository.flush();
        entityManager.clear();

        StudentPointRule reloaded = ruleRepository.findById(saved.getId()).orElseThrow();
        assertEquals("STUDY_RECORD_CORRECT", reloaded.getCode());
    }

    @Test
    void persistsEventAttemptForExistingParentEvent() {
        StudentPointEvent parent = eventRepository.saveAndFlush(
                event("study-record:13:correct:STUDY_RECORD_CORRECT", 13L));
        StudentPointEventAttempt attempt = new StudentPointEventAttempt();
        attempt.setEventId(parent.getId());
        attempt.setAttemptNo(1);
        attempt.setTriggerType(PointAttemptTriggerType.MANUAL);
        attempt.setStatus(PointEventAttemptStatus.FAILED);
        attempt.setOperatorId(3L);
        attempt.setOperatorRole("ADMIN");
        attempt.setReason("retry after investigation");
        attempt.setErrorMessage("temporary failure");
        attempt.setStartedAt(LocalDateTime.of(2026, 7, 21, 12, 0));
        attempt.setFinishedAt(LocalDateTime.of(2026, 7, 21, 12, 1));

        StudentPointEventAttempt saved = eventAttemptRepository.saveAndFlush(attempt);
        entityManager.clear();

        StudentPointEventAttempt reloaded = eventAttemptRepository.findById(saved.getId()).orElseThrow();
        assertEquals(parent.getId(), reloaded.getEventId());
        assertEquals(1, reloaded.getAttemptNo());
        assertEquals(PointAttemptTriggerType.MANUAL, reloaded.getTriggerType());
        assertEquals(PointEventAttemptStatus.FAILED, reloaded.getStatus());
        assertEquals("temporary failure", reloaded.getErrorMessage());
    }

    @Test
    void persistsAdjustmentReversalMetadata() {
        StudentPointAdjustmentRequest request = StudentPointAdjustmentRequest.create(
                "request-12", 12L, 20, "class contribution", 2L, "TEACHER", 10L);
        request.setStatus(PointAdjustmentStatus.REVERSED);
        request.setTransactionId(101L);
        request.setReverseTransactionId(102L);
        request.setReplacedByRequestId(13L);
        request.setReversedAt(LocalDateTime.of(2026, 7, 21, 11, 0));

        StudentPointAdjustmentRequest saved = adjustmentRequestRepository.saveAndFlush(request);

        StudentPointAdjustmentRequest reloaded = adjustmentRequestRepository.findById(saved.getId()).orElseThrow();
        assertEquals("request-12", reloaded.getRequestKey());
        assertEquals(10L, reloaded.getReplacesRequestId());
        assertEquals(13L, reloaded.getReplacedByRequestId());
        assertEquals(102L, reloaded.getReverseTransactionId());
        assertEquals(LocalDateTime.of(2026, 7, 21, 11, 0), reloaded.getReversedAt());
    }

    @Test
    void enforcesGloballyUniqueAdjustmentRequestKey() {
        adjustmentRequestRepository.saveAndFlush(StudentPointAdjustmentRequest.create(
                "same-request-key", 12L, 20, "first", 2L, "TEACHER", null));

        assertThrows(DataIntegrityViolationException.class,
                () -> adjustmentRequestRepository.saveAndFlush(StudentPointAdjustmentRequest.create(
                        "same-request-key", 13L, 30, "second", 3L, "ADMIN", null)));
    }

    @Test
    void startupRecoveryQueryShouldIncludeOnlyResidualProcessingRowsAtCutoff() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 22, 8, 0);
        StudentPointEvent old = event("startup-old", 21L);
        old.setStatus(PointEventStatus.PROCESSING);
        old.setProcessingStartedAt(cutoff.minusSeconds(1));
        old = eventRepository.saveAndFlush(old);
        StudentPointEvent missingTimestamp = event("startup-null", 22L);
        missingTimestamp.setStatus(PointEventStatus.PROCESSING);
        missingTimestamp.setProcessingStartedAt(null);
        missingTimestamp = eventRepository.saveAndFlush(missingTimestamp);
        StudentPointEvent claimedAfterStartup = event("startup-new", 23L);
        claimedAfterStartup.setStatus(PointEventStatus.PROCESSING);
        claimedAfterStartup.setProcessingStartedAt(cutoff.plusSeconds(1));
        claimedAfterStartup = eventRepository.saveAndFlush(claimedAfterStartup);

        List<Long> ids = eventRepository.findInterruptedProcessingEventIdsAfter(
                cutoff, 0L, PageRequest.of(0, 100));

        assertEquals(List.of(old.getId(), missingTimestamp.getId()), ids);
    }

    @Test
    void timeoutRecoveryQueryShouldRetryProcessingRowWithMissingStartTime() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 22, 8, 0);
        StudentPointEvent missingTimestamp = event("timeout-null", 24L);
        missingTimestamp.setStatus(PointEventStatus.PROCESSING);
        missingTimestamp.setProcessingStartedAt(null);
        missingTimestamp = eventRepository.saveAndFlush(missingTimestamp);
        StudentPointEvent recent = event("timeout-recent", 25L);
        recent.setStatus(PointEventStatus.PROCESSING);
        recent.setProcessingStartedAt(cutoff.plusSeconds(1));
        recent = eventRepository.saveAndFlush(recent);

        List<Long> ids = eventRepository.findTimedOutProcessingEventIdsAfter(
                cutoff, 0L, PageRequest.of(0, 100));

        assertEquals(List.of(missingTimestamp.getId()), ids);
    }

    private StudentPointTransaction transaction(String idempotencyKey, Long studentId) {
        StudentPointTransaction transaction = new StudentPointTransaction();
        transaction.setAccountId(1L);
        transaction.setStudentId(studentId);
        transaction.setTransactionType(PointTransactionType.EARN);
        transaction.setAmount(1);
        transaction.setBalanceBefore(0);
        transaction.setBalanceAfter(1);
        transaction.setFrozenBefore(0);
        transaction.setFrozenAfter(0);
        transaction.setSourceType(PointSourceType.STUDY_RECORD);
        transaction.setSourceId(9L);
        transaction.setSourceKey("study-record:9:correct");
        transaction.setRuleCode("STUDY_RECORD_CORRECT");
        transaction.setIdempotencyKey(idempotencyKey);
        return transaction;
    }

    private StudentPointEvent event(String idempotencyKey, Long studentId) {
        StudentPointEvent event = new StudentPointEvent();
        event.setStudentId(studentId);
        event.setSourceType(PointSourceType.STUDY_RECORD);
        event.setSourceId(9L);
        event.setSourceKey("study-record:9:correct");
        event.setRuleCode("STUDY_RECORD_CORRECT");
        event.setRuleName("Correct answer");
        event.setPoints(1);
        event.setIdempotencyKey(idempotencyKey);
        event.setStatus(PointEventStatus.PENDING);
        return event;
    }

    private StudentPointRule rule(String code, String name) {
        return StudentPointRule.create(code, name, PointSourceType.STUDY_RECORD, 1);
    }
}
