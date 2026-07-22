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

        int balanceBefore = account.getAvailablePoints();
        int balanceAfter = addExact(balanceBefore, request.amount());
        if (balanceAfter < 0) {
            throw error("INSUFFICIENT_POINTS", HttpStatus.BAD_REQUEST, "积分余额不足");
        }

        PointTransactionType transactionType;
        if (request.amount() > 0) {
            transactionType = PointTransactionType.EARN;
            account.setLifetimeEarnedPoints(addExact(account.getLifetimeEarnedPoints(), request.amount()));
        } else {
            transactionType = PointTransactionType.DEDUCT;
            account.setLifetimeSpentPoints(
                    addExact(account.getLifetimeSpentPoints(), negateExact(request.amount()))
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
                        "积分流水不存在"
                ));
        if (original.getTransactionType() == PointTransactionType.REVERSE) {
            throw error(
                    "TRANSACTION_ALREADY_REVERSE",
                    HttpStatus.CONFLICT,
                    "冲正流水不能再次冲正"
            );
        }

        StudentPointAdjustmentRequest adjustment = validateManualAdjustment(original);
        StudentPointAccount account = lockActiveAccount(original.getStudentId());
        validateAccountSnapshot(original, account);

        existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return validateIdempotentReversal(existing.get(), originalTransactionId, idempotencyKey);
        }

        int reverseAmount = negateExact(original.getAmount());
        int balanceBefore = account.getAvailablePoints();
        int balanceAfter = addExact(balanceBefore, reverseAmount);
        if (balanceAfter < 0) {
            throw error(
                    "INSUFFICIENT_POINTS_FOR_REVERSAL",
                    HttpStatus.CONFLICT,
                    "积分余额不足，无法冲正"
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
            throw error("INVALID_STUDENT_ID", HttpStatus.BAD_REQUEST, "学生 ID 无效");
        }
        if (request.amount() == null || request.amount() == 0) {
            throw error("INVALID_POINT_AMOUNT", HttpStatus.BAD_REQUEST, "积分变动值不能为零");
        }
        if (request.sourceType() == null) {
            throw error("POINT_SOURCE_TYPE_REQUIRED", HttpStatus.BAD_REQUEST, "积分来源类型不能为空");
        }
        if (isBlank(request.idempotencyKey())) {
            throw error("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST, "幂等键不能为空");
        }
    }

    private StudentPointTransaction validateIdempotentPost(
            StudentPointTransaction existing,
            PostRequest request
    ) {
        boolean matches = Objects.equals(existing.getStudentId(), request.studentId())
                && Objects.equals(existing.getAmount(), request.amount())
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

    private PointTransactionType expectedTransactionType(int amount) {
        return amount > 0 ? PointTransactionType.EARN : PointTransactionType.DEDUCT;
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
                "幂等键已用于其他积分操作"
        );
    }

    private void validateReversalInput(Long originalTransactionId, Actor actor, String reason) {
        if (originalTransactionId == null || originalTransactionId <= 0) {
            throw error("TRANSACTION_ID_REQUIRED", HttpStatus.BAD_REQUEST, "原流水 ID 无效");
        }
        if (actor == null || actor.operatorId() == null || actor.operatorId() <= 0 || isBlank(actor.operatorRole())) {
            throw error("OPERATOR_REQUIRED", HttpStatus.BAD_REQUEST, "冲正操作人不能为空");
        }
        if (!"ADMIN".equalsIgnoreCase(actor.operatorRole())) {
            throw error("ADMIN_OPERATOR_REQUIRED", HttpStatus.FORBIDDEN, "仅管理员可以冲正积分流水");
        }
        if (isBlank(reason)) {
            throw error("REVERSAL_REASON_REQUIRED", HttpStatus.BAD_REQUEST, "冲正原因不能为空");
        }
    }

    private StudentPointAccount lockActiveAccount(Long studentId) {
        StudentPointAccount account = accountRepository.findByStudentIdForUpdate(studentId)
                .orElseThrow(() -> error(
                        "POINT_ACCOUNT_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "学生积分账户不存在"
                ));
        if (account.getStatus() != PointAccountStatus.ACTIVE) {
            throw error("POINT_ACCOUNT_FROZEN", HttpStatus.CONFLICT, "学生积分账户已冻结");
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
                    "积分流水与学生账户不匹配"
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

    private int addExact(int left, int right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw error("POINT_BALANCE_OVERFLOW", HttpStatus.CONFLICT, "积分数值超出允许范围");
        }
    }

    private int negateExact(int value) {
        try {
            return Math.negateExact(value);
        } catch (ArithmeticException ex) {
            throw error("POINT_BALANCE_OVERFLOW", HttpStatus.CONFLICT, "积分数值超出允许范围");
        }
    }

    private StudentPointOperationException manualAdjustmentStateInvalid() {
        return error(
                "MANUAL_ADJUSTMENT_STATE_INVALID",
                HttpStatus.CONFLICT,
                "手工积分调整单状态与原流水不一致"
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
            Integer amount,
            PointSourceType sourceType,
            Long sourceId,
            String sourceKey,
            String ruleCode,
            String idempotencyKey,
            Actor actor,
            String reason
    ) {
    }
}
