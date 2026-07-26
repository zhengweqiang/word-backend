package com.example.words.service;

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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentPointLedgerService {

    private static final String IDEMPOTENCY_CONSTRAINT = "uk_student_point_transactions_idempotency";

    private final StudentPointAccountRepository accountRepository;
    private final StudentPointTransactionRepository transactionRepository;
    private final StudentPointAdjustmentRequestRepository adjustmentRequestRepository;

    @Transactional
    public StudentPointTransaction post(PostRequest request) {
        validatePostRequest(request);
        Optional<StudentPointTransaction> existing = transactionRepository.findByIdempotencyKey(
                request.idempotencyKey()
        );
        if (existing.isPresent()) {
            return validateIdempotentPost(existing.get(), request);
        }

        StudentPointAccount account = lockActiveAccount(request.studentId());

        // The account lock serializes postings for one student. Rechecking here closes the usual late-key race.
        existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return validateIdempotentPost(existing.get(), request);
        }

        BigDecimal balanceBefore = amount(account.getAvailablePoints());
        BigDecimal balanceAfter = add(balanceBefore, request.amount());
        if (isNegative(balanceAfter)) {
            throw error("INSUFFICIENT_POINTS", HttpStatus.BAD_REQUEST, "Insufficient points");
        }

        PointTransactionType transactionType;
        if (isPositive(request.amount())) {
            transactionType = PointTransactionType.EARN;
            account.setLifetimeEarnedPoints(add(amount(account.getLifetimeEarnedPoints()), request.amount()));
        } else {
            transactionType = PointTransactionType.DEDUCT;
            account.setLifetimeSpentPoints(
                    add(amount(account.getLifetimeSpentPoints()), negate(request.amount()))
            );
        }
        account.setAvailablePoints(balanceAfter);

        StudentPointTransaction transaction = new StudentPointTransaction();
        transaction.setAccountId(account.getId());
        transaction.setStudentId(account.getStudentId());
        transaction.setTransactionType(transactionType);
        transaction.setAmount(request.amount());
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setFrozenBefore(account.getFrozenPoints());
        transaction.setFrozenAfter(account.getFrozenPoints());
        transaction.setSourceType(request.sourceType());
        transaction.setSourceId(request.sourceId());
        transaction.setSourceKey(request.sourceKey());
        transaction.setRuleCode(request.ruleCode());
        transaction.setIdempotencyKey(request.idempotencyKey());
        applyActor(transaction, request.actor());
        transaction.setReason(request.reason());

        accountRepository.save(account);
        return saveTransaction(transaction);
    }

    @Transactional
    public StudentPointTransaction reverse(Long originalTransactionId, Actor actor, String reason) {
        validateReversalInput(originalTransactionId, actor, reason);
        String idempotencyKey = "reverse:" + originalTransactionId;
        Optional<StudentPointTransaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return validateIdempotentReversal(existing.get(), originalTransactionId, idempotencyKey);
        }

        StudentPointTransaction original = transactionRepository.findById(originalTransactionId)
                .orElseThrow(() -> error(
                        "TRANSACTION_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "Point transaction does not exist"
                ));
        if (original.getTransactionType() == PointTransactionType.REVERSE) {
            throw error(
                    "TRANSACTION_ALREADY_REVERSE",
                    HttpStatus.CONFLICT,
                    "Reversal transaction cannot be reversed again"
            );
        }

        StudentPointAdjustmentRequest adjustment = validateManualAdjustment(original);
        StudentPointAccount account = lockActiveAccount(original.getStudentId());
        validateAccountSnapshot(original, account);

        existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return validateIdempotentReversal(existing.get(), originalTransactionId, idempotencyKey);
        }

        BigDecimal reverseAmount = negate(original.getAmount());
        BigDecimal balanceBefore = amount(account.getAvailablePoints());
        BigDecimal balanceAfter = add(balanceBefore, reverseAmount);
        if (isNegative(balanceAfter)) {
            throw error(
                    "INSUFFICIENT_POINTS_FOR_REVERSAL",
                    HttpStatus.CONFLICT,
                    "Insufficient points for reversal"
            );
        }

        account.setAvailablePoints(balanceAfter);
        StudentPointTransaction reversal = new StudentPointTransaction();
        reversal.setAccountId(account.getId());
        reversal.setStudentId(account.getStudentId());
        reversal.setTransactionType(PointTransactionType.REVERSE);
        reversal.setAmount(reverseAmount);
        reversal.setBalanceBefore(balanceBefore);
        reversal.setBalanceAfter(balanceAfter);
        reversal.setFrozenBefore(account.getFrozenPoints());
        reversal.setFrozenAfter(account.getFrozenPoints());
        reversal.setSourceType(PointSourceType.ADMIN_CORRECTION);
        reversal.setSourceId(originalTransactionId);
        reversal.setSourceKey(idempotencyKey);
        reversal.setIdempotencyKey(idempotencyKey);
        reversal.setReversedTransactionId(originalTransactionId);
        applyActor(reversal, actor);
        reversal.setReason(reason.trim());

        accountRepository.save(account);
        StudentPointTransaction savedReversal = saveTransaction(reversal);
        if (adjustment != null) {
            adjustment.setStatus(PointAdjustmentStatus.REVERSED);
            adjustment.setReverseTransactionId(savedReversal.getId());
            adjustment.setReversedAt(LocalDateTime.now());
            adjustmentRequestRepository.save(adjustment);
        }
        return savedReversal;
    }

    private void validatePostRequest(PostRequest request) {
        if (request == null || request.studentId() == null || request.studentId() <= 0) {
            throw error("INVALID_STUDENT_ID", HttpStatus.BAD_REQUEST, "Student ID is invalid");
        }
        if (isZero(request.amount())) {
            throw error("INVALID_POINT_AMOUNT", HttpStatus.BAD_REQUEST, "Point amount must not be zero");
        }
        if (request.sourceType() == null) {
            throw error("POINT_SOURCE_TYPE_REQUIRED", HttpStatus.BAD_REQUEST, "Point source type is required");
        }
        if (isBlank(request.idempotencyKey())) {
            throw error("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST, "Idempotency key is required");
        }
    }

    private StudentPointTransaction validateIdempotentPost(
            StudentPointTransaction existing,
            PostRequest request
    ) {
        boolean matches = Objects.equals(existing.getStudentId(), request.studentId())
                && sameAmount(existing.getAmount(), request.amount())
                && existing.getTransactionType() == expectedTransactionType(request.amount())
                && existing.getSourceType() == request.sourceType()
                && Objects.equals(existing.getSourceId(), request.sourceId())
                && Objects.equals(existing.getSourceKey(), request.sourceKey())
                && Objects.equals(existing.getRuleCode(), request.ruleCode());
        if (!matches) {
            throw idempotencyKeyConflict();
        }
        return existing;
    }

    private StudentPointTransaction validateIdempotentReversal(
            StudentPointTransaction existing,
            Long originalTransactionId,
            String idempotencyKey
    ) {
        boolean matches = existing.getTransactionType() == PointTransactionType.REVERSE
                && Objects.equals(existing.getReversedTransactionId(), originalTransactionId)
                && existing.getSourceType() == PointSourceType.ADMIN_CORRECTION
                && Objects.equals(existing.getSourceId(), originalTransactionId)
                && Objects.equals(existing.getIdempotencyKey(), idempotencyKey)
                && Objects.equals(existing.getSourceKey(), idempotencyKey);
        if (!matches) {
            throw idempotencyKeyConflict();
        }
        return existing;
    }

    private PointTransactionType expectedTransactionType(BigDecimal amount) {
        return isPositive(amount) ? PointTransactionType.EARN : PointTransactionType.DEDUCT;
    }

    private StudentPointTransaction saveTransaction(StudentPointTransaction transaction) {
        try {
            return transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException ex) {
            if (isIdempotencyConstraintViolation(ex)) {
                throw idempotencyKeyConflict();
            }
            throw ex;
        }
    }

    private boolean isIdempotencyConstraintViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(IDEMPOTENCY_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private StudentPointOperationException idempotencyKeyConflict() {
        return error(
                "IDEMPOTENCY_KEY_CONFLICT",
                HttpStatus.CONFLICT,
                "Idempotency key is already used by another point operation"
        );
    }

    private void validateReversalInput(Long originalTransactionId, Actor actor, String reason) {
        if (originalTransactionId == null || originalTransactionId <= 0) {
            throw error("TRANSACTION_ID_REQUIRED", HttpStatus.BAD_REQUEST, "Original transaction ID is invalid");
        }
        if (actor == null || actor.operatorId() == null || actor.operatorId() <= 0 || isBlank(actor.operatorRole())) {
            throw error("OPERATOR_REQUIRED", HttpStatus.BAD_REQUEST, "Reversal operator is required");
        }
        if (!"ADMIN".equalsIgnoreCase(actor.operatorRole())) {
            throw error("ADMIN_OPERATOR_REQUIRED", HttpStatus.FORBIDDEN, "Only administrators can reverse points");
        }
        if (isBlank(reason)) {
            throw error("REVERSAL_REASON_REQUIRED", HttpStatus.BAD_REQUEST, "Reversal reason is required");
        }
    }

    private StudentPointAccount lockActiveAccount(Long studentId) {
        StudentPointAccount account = accountRepository.findByStudentIdForUpdate(studentId)
                .orElseThrow(() -> error(
                        "POINT_ACCOUNT_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "Student point account does not exist"
                ));
        if (account.getStatus() != PointAccountStatus.ACTIVE) {
            throw error("POINT_ACCOUNT_FROZEN", HttpStatus.CONFLICT, "Student point account is frozen");
        }
        return account;
    }

    private StudentPointAdjustmentRequest validateManualAdjustment(StudentPointTransaction original) {
        if (original.getSourceType() != PointSourceType.MANUAL_ADJUSTMENT) {
            return null;
        }
        if (original.getSourceId() == null) {
            throw manualAdjustmentStateInvalid();
        }
        StudentPointAdjustmentRequest adjustment = adjustmentRequestRepository.findById(original.getSourceId())
                .orElseThrow(this::manualAdjustmentStateInvalid);
        if (adjustment.getStatus() != PointAdjustmentStatus.APPLIED
                || !original.getId().equals(adjustment.getTransactionId())
                || !original.getStudentId().equals(adjustment.getStudentId())) {
            throw manualAdjustmentStateInvalid();
        }
        return adjustment;
    }

    private void validateAccountSnapshot(StudentPointTransaction original, StudentPointAccount account) {
        if (!account.getId().equals(original.getAccountId())
                || !account.getStudentId().equals(original.getStudentId())) {
            throw error(
                    "POINT_ACCOUNT_MISMATCH",
                    HttpStatus.CONFLICT,
                    "Point transaction does not match the student account"
            );
        }
    }

    private void applyActor(StudentPointTransaction transaction, Actor actor) {
        if (actor == null) {
            return;
        }
        transaction.setOperatorId(actor.operatorId());
        transaction.setOperatorRole(actor.operatorRole());
    }

    private BigDecimal add(BigDecimal left, BigDecimal right) {
        return amount(left).add(amount(right));
    }

    private BigDecimal negate(BigDecimal value) {
        return amount(value).negate();
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) < 0;
    }

    private boolean isZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private StudentPointOperationException manualAdjustmentStateInvalid() {
        return error(
                "MANUAL_ADJUSTMENT_STATE_INVALID",
                HttpStatus.CONFLICT,
                "Manual point adjustment state does not match the original transaction"
        );
    }

    private StudentPointOperationException error(String code, HttpStatus status, String message) {
        return new StudentPointOperationException(code, status, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record Actor(Long operatorId, String operatorRole) {
    }

    public record PostRequest(
            Long studentId,
            BigDecimal amount,
            PointSourceType sourceType,
            Long sourceId,
            String sourceKey,
            String ruleCode,
            String idempotencyKey,
            Actor actor,
            String reason
    ) {
        public PostRequest(
                Long studentId,
                int amount,
                PointSourceType sourceType,
                Long sourceId,
                String sourceKey,
                String ruleCode,
                String idempotencyKey,
                Actor actor,
                String reason
        ) {
            this(studentId, BigDecimal.valueOf(amount), sourceType, sourceId, sourceKey, ruleCode, idempotencyKey,
                    actor, reason);
        }
    }
}
