package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.example.words.model.PointAdjustmentStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.PointTransactionType;
import com.example.words.model.StudentPointAccount;
import com.example.words.model.StudentPointAdjustmentRequest;
import com.example.words.model.StudentPointTransaction;
import com.example.words.repository.StudentPointAccountRepository;
import com.example.words.repository.StudentPointAdjustmentRequestRepository;
import com.example.words.repository.StudentPointTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(StudentPointLedgerService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentPointLedgerTransactionIntegrationTest {

    private static final StudentPointLedgerService.Actor ADMIN_ACTOR =
            new StudentPointLedgerService.Actor(1L, "ADMIN");

    @Autowired
    private StudentPointLedgerService ledgerService;

    @Autowired
    private StudentPointAccountRepository accountRepository;

    @Autowired
    private StudentPointTransactionRepository transactionRepository;

    @SpyBean
    private StudentPointAdjustmentRequestRepository adjustmentRequestRepository;

    @BeforeEach
    void setUp() {
        adjustmentRequestRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void reverseManualAdjustmentShouldCommitBalanceTransactionAndRequestClosure() {
        Scenario scenario = persistAppliedManualAdjustment();

        StudentPointTransaction reversal = ledgerService.reverse(
                scenario.originalTransactionId(),
                ADMIN_ACTOR,
                "reverse mistaken adjustment"
        );

        assertNotNull(reversal.getId());
        StudentPointAccount account = accountRepository.findById(scenario.accountId()).orElseThrow();
        assertEquals(12, account.getAvailablePoints());
        StudentPointTransaction persistedReversal = transactionRepository.findById(reversal.getId()).orElseThrow();
        assertEquals(PointTransactionType.REVERSE, persistedReversal.getTransactionType());
        assertEquals(-8, persistedReversal.getAmount());

        StudentPointAdjustmentRequest request = adjustmentRequestRepository
                .findById(scenario.adjustmentRequestId())
                .orElseThrow();
        assertEquals(PointAdjustmentStatus.REVERSED, request.getStatus());
        assertEquals(reversal.getId(), request.getReverseTransactionId());
        assertNotNull(request.getReversedAt());
    }

    @Test
    void reverseManualAdjustmentShouldRollbackAllWritesWhenRequestSaveFails() {
        Scenario scenario = persistAppliedManualAdjustment();
        doThrow(new DataIntegrityViolationException("forced adjustment save failure"))
                .when(adjustmentRequestRepository)
                .save(any(StudentPointAdjustmentRequest.class));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> ledgerService.reverse(
                        scenario.originalTransactionId(),
                        ADMIN_ACTOR,
                        "force rollback"
                )
        );

        StudentPointAccount account = accountRepository.findById(scenario.accountId()).orElseThrow();
        assertEquals(20, account.getAvailablePoints());
        assertEquals(1, transactionRepository.count());
        StudentPointAdjustmentRequest request = adjustmentRequestRepository
                .findById(scenario.adjustmentRequestId())
                .orElseThrow();
        assertEquals(PointAdjustmentStatus.APPLIED, request.getStatus());
        assertNull(request.getReverseTransactionId());
        assertNull(request.getReversedAt());
    }

    private Scenario persistAppliedManualAdjustment() {
        StudentPointAccount account = StudentPointAccount.create(42L);
        account.setAvailablePoints(20);
        account.setLifetimeEarnedPoints(20);
        account = accountRepository.saveAndFlush(account);

        StudentPointAdjustmentRequest request = StudentPointAdjustmentRequest.create(
                "ledger-reversal-request", 42L, 8, "manual award", 2L, "TEACHER", null);
        request = adjustmentRequestRepository.saveAndFlush(request);

        StudentPointTransaction original = new StudentPointTransaction();
        original.setAccountId(account.getId());
        original.setStudentId(42L);
        original.setTransactionType(PointTransactionType.EARN);
        original.setAmount(8);
        original.setBalanceBefore(12);
        original.setBalanceAfter(20);
        original.setFrozenBefore(0);
        original.setFrozenAfter(0);
        original.setSourceType(PointSourceType.MANUAL_ADJUSTMENT);
        original.setSourceId(request.getId());
        original.setSourceKey("manual-adjustment:" + request.getId());
        original.setIdempotencyKey("manual-adjustment:" + request.getId());
        original = transactionRepository.saveAndFlush(original);

        request.setStatus(PointAdjustmentStatus.APPLIED);
        request.setTransactionId(original.getId());
        adjustmentRequestRepository.saveAndFlush(request);
        return new Scenario(account.getId(), original.getId(), request.getId());
    }

    private record Scenario(Long accountId, Long originalTransactionId, Long adjustmentRequestId) {
    }
}
