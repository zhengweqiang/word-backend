package com.example.words.dto;

import com.example.words.model.PointSourceType;
import com.example.words.model.PointTransactionType;
import com.example.words.model.StudentPointTransaction;
import java.time.LocalDateTime;

public record StudentPointTransactionResponse(
        Long id,
        Long accountId,
        Long studentId,
        PointTransactionType transactionType,
        Integer amount,
        Integer balanceBefore,
        Integer balanceAfter,
        PointSourceType sourceType,
        Long sourceId,
        String sourceKey,
        String ruleCode,
        Long operatorId,
        String operatorRole,
        String reason,
        Long reversedTransactionId,
        LocalDateTime createdAt
) {
    public static StudentPointTransactionResponse from(StudentPointTransaction transaction) {
        return new StudentPointTransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getStudentId(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getBalanceBefore(),
                transaction.getBalanceAfter(),
                transaction.getSourceType(),
                transaction.getSourceId(),
                transaction.getSourceKey(),
                transaction.getRuleCode(),
                transaction.getOperatorId(),
                transaction.getOperatorRole(),
                transaction.getReason(),
                transaction.getReversedTransactionId(),
                transaction.getCreatedAt()
        );
    }
}
