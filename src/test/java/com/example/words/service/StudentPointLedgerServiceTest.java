package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.PointAccountStatus;
import com.example.words.model.PointAdjustmentStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.PointTransactionType;
import com.example.words.model.StudentPointAccount;
import com.example.words.model.StudentPointAdjustmentRequest;
import com.example.words.model.StudentPointTransaction;
import com.example.words.repository.StudentPointAccountRepository;
import com.example.words.repository.StudentPointAdjustmentRequestRepository;
import com.example.words.repository.StudentPointTransactionRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class StudentPointLedgerServiceTest {

    private static final StudentPointLedgerService.Actor SYSTEM_ACTOR =
            new StudentPointLedgerService.Actor(9L, "SYSTEM");
    private static final StudentPointLedgerService.Actor ADMIN_ACTOR =
            new StudentPointLedgerService.Actor(1L, "ADMIN");

    @Mock
    private StudentPointAccountRepository accountRepository;

    @Mock
    private StudentPointTransactionRepository transactionRepository;

    @Mock
    private StudentPointAdjustmentRequestRepository adjustmentRequestRepository;

    private StudentPointLedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new StudentPointLedgerService(
                accountRepository,
                transactionRepository,
                adjustmentRequestRepository
        );
        AtomicLong transactionIds = new AtomicLong(100L);
        lenient().when(transactionRepository.saveAndFlush(any(StudentPointTransaction.class)))
                .thenAnswer(invocation -> {
            StudentPointTransaction transaction = invocation.getArgument(0);
            if (transaction.getId() == null) {
                transaction.setId(transactionIds.getAndIncrement());
            }
            return transaction;
                });
    }

    @Test
    void postShouldEarnPointsAndCaptureImmutableSnapshots() {
        StudentPointAccount account = account(7L, 42L, 12, 3, 20, 5, PointAccountStatus.ACTIVE);
        when(transactionRepository.findByIdempotencyKey("study:99")).thenReturn(Optional.empty());
        when(accountRepository.findByStudentIdForUpdate(42L)).thenReturn(Optional.of(account));

        StudentPointTransaction result = ledgerService.post(new StudentPointLedgerService.PostRequest(
                42L, 5, PointSourceType.STUDY_RECORD, 99L, "record:99",
                "STUDY_RECORD_CORRECT", "study:99", SYSTEM_ACTOR, "答对单词"
        ));

        assertEquals(PointTransactionType.EARN, result.getTransactionType());
        assertEquals(5, result.getAmount());
        assertEquals(12, result.getBalanceBefore());
        assertEquals(17, result.getBalanceAfter());
        assertEquals(3, result.getFrozenBefore());
        assertEquals(3, result.getFrozenAfter());
        assertEquals(7L, result.getAccountId());
        assertEquals(42L, result.getStudentId());
        assertEquals(99L, result.getSourceId());
        assertEquals("record:99", result.getSourceKey());
        assertEquals("STUDY_RECORD_CORRECT", result.getRuleCode());
        assertEquals(9L, result.getOperatorId());
        assertEquals("SYSTEM", result.getOperatorRole());
        assertEquals("答对单词", result.getReason());
        assertEquals(17, account.getAvailablePoints());
        assertEquals(25, account.getLifetimeEarnedPoints());
        assertEquals(5, account.getLifetimeSpentPoints());
        verify(accountRepository).save(account);
    }

    @Test
    void postShouldReturnExistingTransactionWithoutLockingAccount() {
        StudentPointTransaction existing = matchingTransaction(81L, 42L, 5, "same-key");
        when(transactionRepository.findByIdempotencyKey("same-key")).thenReturn(Optional.of(existing));

        StudentPointTransaction result = ledgerService.post(new StudentPointLedgerService.PostRequest(
                42L, 5, PointSourceType.STUDY_RECORD, 99L, null,
                null, "same-key", SYSTEM_ACTOR, null
        ));

        assertSame(existing, result);
        verify(accountRepository, never()).findByStudentIdForUpdate(any());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void postShouldRejectExistingIdempotencyKeyOwnedByAnotherStudent() {
        StudentPointTransaction existing = matchingTransaction(81L, 99L, 5, "shared-key");
        when(transactionRepository.findByIdempotencyKey("shared-key")).thenReturn(Optional.of(existing));

        StudentPointOperationException failure = assertThrows(
                StudentPointOperationException.class,
                () -> ledgerService.post(postRequest(42L, 5, "shared-key"))
        );

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", failure.getCode());
        assertEquals(HttpStatus.CONFLICT, failure.getStatus());
        verify(accountRepository, never()).findByStudentIdForUpdate(any());
    }

    @Test
    void postShouldRejectExistingIdempotencyKeyWithDifferentAmountOrSourceMetadata() {
        StudentPointTransaction existing = matchingTransaction(81L, 42L, 5, "shared-key");
        when(transactionRepository.findByIdempotencyKey("shared-key")).thenReturn(Optional.of(existing));

        assertIdempotencyConflict(new StudentPointLedgerService.PostRequest(
                42L, 6, PointSourceType.STUDY_RECORD, 99L, null,
                null, "shared-key", SYSTEM_ACTOR, null
        ));
        assertIdempotencyConflict(new StudentPointLedgerService.PostRequest(
                42L, 5, PointSourceType.STUDY_TASK, 99L, null,
                null, "shared-key", SYSTEM_ACTOR, null
        ));
        assertIdempotencyConflict(new StudentPointLedgerService.PostRequest(
                42L, 5, PointSourceType.STUDY_RECORD, 100L, null,
                null, "shared-key", SYSTEM_ACTOR, null
        ));
        assertIdempotencyConflict(new StudentPointLedgerService.PostRequest(
                42L, 5, PointSourceType.STUDY_RECORD, 99L, "different-source-key",
                null, "shared-key", SYSTEM_ACTOR, null
        ));
        assertIdempotencyConflict(new StudentPointLedgerService.PostRequest(
                42L, 5, PointSourceType.STUDY_RECORD, 99L, null,
                "DIFFERENT_RULE", "shared-key", SYSTEM_ACTOR, null
        ));
    }

    @Test
    void postShouldRejectExistingIdempotencyKeyWithWrongTransactionType() {
        StudentPointTransaction positiveAsDeduct = matchingTransaction(81L, 42L, 5, "positive-key");
        positiveAsDeduct.setTransactionType(PointTransactionType.DEDUCT);
        when(transactionRepository.findByIdempotencyKey("positive-key"))
                .thenReturn(Optional.of(positiveAsDeduct));
        assertIdempotencyConflict(postRequest(42L, 5, "positive-key"));

        StudentPointTransaction negativeAsEarn = matchingTransaction(82L, 42L, -5, "negative-key");
        negativeAsEarn.setTransactionType(PointTransactionType.EARN);
        when(transactionRepository.findByIdempotencyKey("negative-key"))
                .thenReturn(Optional.of(negativeAsEarn));
        assertIdempotencyConflict(postRequest(42L, -5, "negative-key"));
    }

    @Test
    void postShouldResolveIdempotencyKeyThatAppearsWhileWaitingForAccountLock() {
        StudentPointTransaction existing = matchingTransaction(81L, 42L, 5, "late-key");
        StudentPointAccount account = account(7L, 42L, 12, 0, 20, 5, PointAccountStatus.ACTIVE);
        when(transactionRepository.findByIdempotencyKey("late-key"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(accountRepository.findByStudentIdForUpdate(42L)).thenReturn(Optional.of(account));

        StudentPointTransaction result = ledgerService.post(postRequest(42L, 5, "late-key"));

        assertSame(existing, result);
        assertEquals(12, account.getAvailablePoints());
        assertEquals(20, account.getLifetimeEarnedPoints());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void postShouldValidateIdempotencyPayloadAfterAccountLock() {
        StudentPointTransaction conflicting = matchingTransaction(81L, 42L, 6, "late-key");
        StudentPointAccount account = account(7L, 42L, 12, 0, 20, 5, PointAccountStatus.ACTIVE);
        when(transactionRepository.findByIdempotencyKey("late-key"))
                .thenReturn(Optional.empty(), Optional.of(conflicting));
        when(accountRepository.findByStudentIdForUpdate(42L)).thenReturn(Optional.of(account));

        assertIdempotencyConflict(postRequest(42L, 5, "late-key"));

        assertEquals(12, account.getAvailablePoints());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void postShouldConvertNamedIdempotencyConstraintFailureToStableConflict() {
        StudentPointAccount account = account(7L, 42L, 12, 0, 20, 5, PointAccountStatus.ACTIVE);
        when(transactionRepository.findByIdempotencyKey("race-key")).thenReturn(Optional.empty());
        when(accountRepository.findByStudentIdForUpdate(42L)).thenReturn(Optional.of(account));
        when(transactionRepository.saveAndFlush(any(StudentPointTransaction.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement",
                        new IllegalStateException(
                                "duplicate key violates constraint UK_STUDENT_POINT_TRANSACTIONS_IDEMPOTENCY"
                        )
                ));

        StudentPointOperationException failure = assertThrows(
                StudentPointOperationException.class,
                () -> ledgerService.post(postRequest(42L, 5, "race-key"))
        );

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", failure.getCode());
        assertEquals(HttpStatus.CONFLICT, failure.getStatus());
    }

    @Test
    void postShouldPropagateUnrelatedIntegrityFailure() {
        StudentPointAccount account = account(7L, 42L, 12, 0, 20, 5, PointAccountStatus.ACTIVE);
        when(transactionRepository.findByIdempotencyKey("other-constraint")).thenReturn(Optional.empty());
        when(accountRepository.findByStudentIdForUpdate(42L)).thenReturn(Optional.of(account));
        DataIntegrityViolationException integrityFailure = new DataIntegrityViolationException(
                "violates constraint ck_student_point_transactions_amount"
        );
        when(transactionRepository.saveAndFlush(any(StudentPointTransaction.class)))
                .thenThrow(integrityFailure);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> ledgerService.post(postRequest(42L, 5, "other-constraint"))
        );

        assertSame(integrityFailure, thrown);
    }

    @Test
    void postShouldDeductPointsAndRejectInsufficientBalance() {
        StudentPointAccount account = account(7L, 42L, 12, 0, 20, 5, PointAccountStatus.ACTIVE);
        when(transactionRepository.findByIdempotencyKey("deduct:1")).thenReturn(Optional.empty());
        when(accountRepository.findByStudentIdForUpdate(42L)).thenReturn(Optional.of(account));

        StudentPointTransaction result = ledgerService.post(new StudentPointLedgerService.PostRequest(
                42L, -7, PointSourceType.REDEMPTION, 3L, null,
                null, "deduct:1", SYSTEM_ACTOR, "兑换"
        ));

        assertEquals(PointTransactionType.DEDUCT, result.getTransactionType());
        assertEquals(5, account.getAvailablePoints());
        assertEquals(12, account.getLifetimeSpentPoints());

        StudentPointAccount insufficient = account(8L, 43L, 2, 0, 2, 0, PointAccountStatus.ACTIVE);
        when(transactionRepository.findByIdempotencyKey("deduct:2")).thenReturn(Optional.empty());
        when(accountRepository.findByStudentIdForUpdate(43L)).thenReturn(Optional.of(insufficient));

        StudentPointOperationException failure = assertThrows(
                StudentPointOperationException.class,
                () -> ledgerService.post(new StudentPointLedgerService.PostRequest(
                        43L, -3, PointSourceType.REDEMPTION, 4L, null,
                        null, "deduct:2", SYSTEM_ACTOR, "兑换"
                ))
        );
        assertEquals("INSUFFICIENT_POINTS", failure.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
        assertEquals(2, insufficient.getAvailablePoints());
    }

    @Test
    void postShouldRejectMissingOrFrozenAccount() {
        when(transactionRepository.findByIdempotencyKey("missing")).thenReturn(Optional.empty());
        when(accountRepository.findByStudentIdForUpdate(42L)).thenReturn(Optional.empty());

        StudentPointOperationException missing = assertThrows(
                StudentPointOperationException.class,
                () -> ledgerService.post(postRequest(42L, 1, "missing"))
        );
        assertEquals("POINT_ACCOUNT_NOT_FOUND", missing.getCode());
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatus());

        StudentPointAccount frozen = account(7L, 43L, 3, 0, 3, 0, PointAccountStatus.FROZEN);
        when(transactionRepository.findByIdempotencyKey("frozen")).thenReturn(Optional.empty());
        when(accountRepository.findByStudentIdForUpdate(43L)).thenReturn(Optional.of(frozen));

        StudentPointOperationException frozenFailure = assertThrows(
                StudentPointOperationException.class,
                () -> ledgerService.post(postRequest(43L, 1, "frozen"))
        );
        assertEquals("POINT_ACCOUNT_FROZEN", frozenFailure.getCode());
        assertEquals(HttpStatus.CONFLICT, frozenFailure.getStatus());
    }

    @Test
    void postShouldRejectRequiredInputErrors() {
        assertCode("INVALID_STUDENT_ID", () -> ledgerService.post(postRequest(null, 1, "key")));
        assertCode("INVALID_POINT_AMOUNT", () -> ledgerService.post(postRequest(42L, 0, "key")));
        assertCode("POINT_SOURCE_TYPE_REQUIRED", () -> ledgerService.post(new StudentPointLedgerService.PostRequest(
                42L, 1, null, null, null, null, "key", SYSTEM_ACTOR, null
        )));
        assertCode("IDEMPOTENCY_KEY_REQUIRED", () -> ledgerService.post(postRequest(42L, 1, " ")));
    }

    @Test
    void reverseShouldFullyReversePositiveAwardWithoutChangingLifetimeCounters() {
        StudentPointTransaction original = transaction(77L, 7L, 42L, 10, PointTransactionType.EARN);
        StudentPointAccount account = account(7L, 42L, 15, 2, 30, 4, PointAccountStatus.ACTIVE);
        prepareReversal(original, account);

        StudentPointTransaction reversal = ledgerService.reverse(77L, ADMIN_ACTOR, "误发积分");

        assertEquals(PointTransactionType.REVERSE, reversal.getTransactionType());
        assertEquals(-10, reversal.getAmount());
        assertEquals(15, reversal.getBalanceBefore());
        assertEquals(5, reversal.getBalanceAfter());
        assertEquals(PointSourceType.ADMIN_CORRECTION, reversal.getSourceType());
        assertEquals(77L, reversal.getSourceId());
        assertEquals("reverse:77", reversal.getSourceKey());
        assertEquals("reverse:77", reversal.getIdempotencyKey());
        assertEquals(77L, reversal.getReversedTransactionId());
        assertEquals(1L, reversal.getOperatorId());
        assertEquals("ADMIN", reversal.getOperatorRole());
        assertEquals("误发积分", reversal.getReason());
        assertEquals(5, account.getAvailablePoints());
        assertEquals(30, account.getLifetimeEarnedPoints());
        assertEquals(4, account.getLifetimeSpentPoints());
    }

    @Test
    void reverseShouldRestorePointsFromNegativeDeduction() {
        StudentPointTransaction original = transaction(78L, 7L, 42L, -4, PointTransactionType.DEDUCT);
        StudentPointAccount account = account(7L, 42L, 6, 0, 20, 10, PointAccountStatus.ACTIVE);
        prepareReversal(original, account);

        StudentPointTransaction reversal = ledgerService.reverse(78L, ADMIN_ACTOR, "撤销扣减");

        assertEquals(4, reversal.getAmount());
        assertEquals(10, account.getAvailablePoints());
        assertEquals(20, account.getLifetimeEarnedPoints());
        assertEquals(10, account.getLifetimeSpentPoints());
    }

    @Test
    void reverseShouldBeIdempotent() {
        StudentPointTransaction existing = matchingReversal(90L, 77L);
        when(transactionRepository.findByIdempotencyKey("reverse:77")).thenReturn(Optional.of(existing));

        StudentPointTransaction result = ledgerService.reverse(77L, ADMIN_ACTOR, "重复请求");

        assertSame(existing, result);
        verify(transactionRepository, never()).findById(77L);
        verify(accountRepository, never()).findByStudentIdForUpdate(any());
    }

    @Test
    void reverseShouldRejectOrdinaryTransactionOccupyingReversalIdempotencyKey() {
        StudentPointTransaction occupied = transaction(90L, 7L, 42L, 5, PointTransactionType.EARN);
        occupied.setIdempotencyKey("reverse:77");
        occupied.setSourceType(PointSourceType.STUDY_RECORD);
        occupied.setSourceId(77L);
        occupied.setSourceKey("reverse:77");
        when(transactionRepository.findByIdempotencyKey("reverse:77")).thenReturn(Optional.of(occupied));

        assertIdempotencyConflict(() -> ledgerService.reverse(77L, ADMIN_ACTOR, "occupied key"));
        verify(transactionRepository, never()).findById(77L);
        verify(accountRepository, never()).findByStudentIdForUpdate(any());
    }

    @Test
    void reverseShouldValidateIdempotencyPayloadAfterAccountLock() {
        StudentPointTransaction original = transaction(77L, 7L, 42L, 5, PointTransactionType.EARN);
        StudentPointTransaction occupied = matchingReversal(90L, 999L);
        StudentPointAccount account = account(7L, 42L, 10, 0, 10, 0, PointAccountStatus.ACTIVE);
        when(transactionRepository.findByIdempotencyKey("reverse:77"))
                .thenReturn(Optional.empty(), Optional.of(occupied));
        when(transactionRepository.findById(77L)).thenReturn(Optional.of(original));
        when(accountRepository.findByStudentIdForUpdate(42L)).thenReturn(Optional.of(account));

        assertIdempotencyConflict(() -> ledgerService.reverse(77L, ADMIN_ACTOR, "occupied after lock"));
        assertEquals(10, account.getAvailablePoints());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void reverseShouldRejectInsufficientBalanceWithMandatedCode() {
        StudentPointTransaction original = transaction(77L, 7L, 42L, 10, PointTransactionType.EARN);
        StudentPointAccount account = account(7L, 42L, 5, 0, 20, 0, PointAccountStatus.ACTIVE);
        prepareReversal(original, account);

        StudentPointOperationException failure = assertThrows(
                StudentPointOperationException.class,
                () -> ledgerService.reverse(77L, ADMIN_ACTOR, "余额不足"
                )
        );

        assertEquals("INSUFFICIENT_POINTS_FOR_REVERSAL", failure.getCode());
        assertEquals(HttpStatus.CONFLICT, failure.getStatus());
        assertEquals(5, account.getAvailablePoints());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void reverseShouldRejectMissingTransactionAndReversingAReversal() {
        when(transactionRepository.findByIdempotencyKey("reverse:404")).thenReturn(Optional.empty());
        when(transactionRepository.findById(404L)).thenReturn(Optional.empty());
        assertCode("TRANSACTION_NOT_FOUND", () -> ledgerService.reverse(404L, ADMIN_ACTOR, "不存在"));

        StudentPointTransaction reversal = transaction(88L, 7L, 42L, -3, PointTransactionType.REVERSE);
        when(transactionRepository.findByIdempotencyKey("reverse:88")).thenReturn(Optional.empty());
        when(transactionRepository.findById(88L)).thenReturn(Optional.of(reversal));
        assertCode("TRANSACTION_ALREADY_REVERSE", () -> ledgerService.reverse(88L, ADMIN_ACTOR, "再次冲正"));
    }

    @Test
    void reverseShouldRejectAccountSnapshotMismatchAndInvalidInput() {
        StudentPointTransaction original = transaction(77L, 7L, 42L, 3, PointTransactionType.EARN);
        StudentPointAccount wrongAccount = account(8L, 42L, 10, 0, 10, 0, PointAccountStatus.ACTIVE);
        prepareReversal(original, wrongAccount);

        assertCode("POINT_ACCOUNT_MISMATCH", () -> ledgerService.reverse(77L, ADMIN_ACTOR, "账户不一致"));
        assertCode("TRANSACTION_ID_REQUIRED", () -> ledgerService.reverse(null, ADMIN_ACTOR, "原因"));
        assertCode("OPERATOR_REQUIRED", () -> ledgerService.reverse(1L, null, "原因"));
        assertCode("REVERSAL_REASON_REQUIRED", () -> ledgerService.reverse(1L, ADMIN_ACTOR, " "));
    }

    @Test
    void reverseShouldCloseAppliedManualAdjustment() {
        StudentPointTransaction original = transaction(77L, 7L, 42L, 8, PointTransactionType.EARN);
        original.setSourceType(PointSourceType.MANUAL_ADJUSTMENT);
        original.setSourceId(55L);
        StudentPointAdjustmentRequest adjustment = adjustment(55L, 42L, 77L, PointAdjustmentStatus.APPLIED);
        StudentPointAccount account = account(7L, 42L, 10, 0, 10, 0, PointAccountStatus.ACTIVE);
        when(adjustmentRequestRepository.findById(55L)).thenReturn(Optional.of(adjustment));
        prepareReversal(original, account);

        StudentPointTransaction reversal = ledgerService.reverse(77L, ADMIN_ACTOR, "撤销手工调整");

        assertEquals(PointAdjustmentStatus.REVERSED, adjustment.getStatus());
        assertEquals(reversal.getId(), adjustment.getReverseTransactionId());
        assertTrue(adjustment.getReversedAt() != null);
        verify(adjustmentRequestRepository).save(adjustment);
    }

    @Test
    void reverseShouldRejectMissingOrMismatchedManualAdjustmentBeforeMutation() {
        StudentPointTransaction original = transaction(77L, 7L, 42L, 8, PointTransactionType.EARN);
        original.setSourceType(PointSourceType.MANUAL_ADJUSTMENT);
        original.setSourceId(55L);
        when(transactionRepository.findByIdempotencyKey("reverse:77")).thenReturn(Optional.empty());
        when(transactionRepository.findById(77L)).thenReturn(Optional.of(original));
        when(adjustmentRequestRepository.findById(55L)).thenReturn(Optional.empty());

        assertCode("MANUAL_ADJUSTMENT_STATE_INVALID",
                () -> ledgerService.reverse(77L, ADMIN_ACTOR, "缺少调整单"));
        verify(accountRepository, never()).findByStudentIdForUpdate(any());
        verify(transactionRepository, never()).saveAndFlush(any());

        StudentPointAdjustmentRequest wrong = adjustment(55L, 42L, 999L, PointAdjustmentStatus.APPLIED);
        when(adjustmentRequestRepository.findById(55L)).thenReturn(Optional.of(wrong));
        assertCode("MANUAL_ADJUSTMENT_STATE_INVALID",
                () -> ledgerService.reverse(77L, ADMIN_ACTOR, "调整单不匹配"));

        StudentPointAdjustmentRequest wrongStatus = adjustment(55L, 42L, 77L, PointAdjustmentStatus.FAILED);
        when(adjustmentRequestRepository.findById(55L)).thenReturn(Optional.of(wrongStatus));
        assertCode("MANUAL_ADJUSTMENT_STATE_INVALID",
                () -> ledgerService.reverse(77L, ADMIN_ACTOR, "调整单状态错误"));
    }

    private void prepareReversal(StudentPointTransaction original, StudentPointAccount account) {
        when(transactionRepository.findByIdempotencyKey("reverse:" + original.getId())).thenReturn(Optional.empty());
        when(transactionRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(accountRepository.findByStudentIdForUpdate(original.getStudentId())).thenReturn(Optional.of(account));
    }

    private StudentPointLedgerService.PostRequest postRequest(Long studentId, Integer amount, String key) {
        return new StudentPointLedgerService.PostRequest(
                studentId, amount, PointSourceType.STUDY_RECORD, 99L,
                null, null, key, SYSTEM_ACTOR, null
        );
    }

    private StudentPointAccount account(
            Long id,
            Long studentId,
            int available,
            int frozen,
            int lifetimeEarned,
            int lifetimeSpent,
            PointAccountStatus status
    ) {
        StudentPointAccount account = new StudentPointAccount();
        account.setId(id);
        account.setStudentId(studentId);
        account.setAvailablePoints(available);
        account.setFrozenPoints(frozen);
        account.setLifetimeEarnedPoints(lifetimeEarned);
        account.setLifetimeSpentPoints(lifetimeSpent);
        account.setStatus(status);
        return account;
    }

    private StudentPointTransaction transaction(
            Long id,
            Long accountId,
            Long studentId,
            int amount,
            PointTransactionType type
    ) {
        StudentPointTransaction transaction = new StudentPointTransaction();
        transaction.setId(id);
        transaction.setAccountId(accountId);
        transaction.setStudentId(studentId);
        transaction.setAmount(amount);
        transaction.setTransactionType(type);
        transaction.setSourceType(PointSourceType.STUDY_RECORD);
        transaction.setIdempotencyKey("original:" + id);
        return transaction;
    }

    private StudentPointTransaction matchingTransaction(Long id, Long studentId, int amount, String idempotencyKey) {
        StudentPointTransaction transaction = transaction(id, 7L, studentId, amount, PointTransactionType.EARN);
        transaction.setSourceId(99L);
        transaction.setSourceKey(null);
        transaction.setRuleCode(null);
        transaction.setIdempotencyKey(idempotencyKey);
        return transaction;
    }

    private StudentPointTransaction matchingReversal(Long id, Long originalTransactionId) {
        StudentPointTransaction transaction = transaction(id, 7L, 42L, -5, PointTransactionType.REVERSE);
        String idempotencyKey = "reverse:" + originalTransactionId;
        transaction.setReversedTransactionId(originalTransactionId);
        transaction.setSourceType(PointSourceType.ADMIN_CORRECTION);
        transaction.setSourceId(originalTransactionId);
        transaction.setSourceKey(idempotencyKey);
        transaction.setIdempotencyKey(idempotencyKey);
        return transaction;
    }

    private StudentPointAdjustmentRequest adjustment(
            Long id,
            Long studentId,
            Long transactionId,
            PointAdjustmentStatus status
    ) {
        StudentPointAdjustmentRequest adjustment = StudentPointAdjustmentRequest.create(
                "ledger-service-" + id, studentId, 1, "test", 1L, "ADMIN", null);
        adjustment.setId(id);
        adjustment.setTransactionId(transactionId);
        adjustment.setStatus(status);
        return adjustment;
    }

    private void assertCode(String code, Runnable operation) {
        StudentPointOperationException failure = assertThrows(StudentPointOperationException.class, operation::run);
        assertEquals(code, failure.getCode());
    }

    private void assertIdempotencyConflict(StudentPointLedgerService.PostRequest request) {
        StudentPointOperationException failure = assertThrows(
                StudentPointOperationException.class,
                () -> ledgerService.post(request)
        );
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", failure.getCode());
        assertEquals(HttpStatus.CONFLICT, failure.getStatus());
    }

    private void assertIdempotencyConflict(Runnable operation) {
        StudentPointOperationException failure = assertThrows(
                StudentPointOperationException.class,
                operation::run
        );
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", failure.getCode());
        assertEquals(HttpStatus.CONFLICT, failure.getStatus());
    }
}
