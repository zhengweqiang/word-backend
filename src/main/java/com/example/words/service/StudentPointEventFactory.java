package com.example.words.service;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class StudentPointEventFactory {

    static final String MANUAL_ADJUSTMENT_RULE_CODE = "MANUAL_ADJUSTMENT";
    static final String MANUAL_ADJUSTMENT_RULE_NAME = "\u4eba\u5de5\u79ef\u5206\u8c03\u6574";
    private static final String MANUAL_ADJUSTMENT_SOURCE_PREFIX = "manual-adjustment:";
    private static final int OPERATOR_ROLE_MAX_LENGTH = 32;
    private static final int REASON_MAX_LENGTH = 500;

    public StudentPointEvent manualAdjustment(
            Long studentId,
            Long adjustmentRequestId,
            Integer amount,
            Long operatorId,
            String operatorRole,
            String reason
    ) {
        validatePositive(studentId, "studentId");
        validatePositive(adjustmentRequestId, "adjustmentRequestId");
        if (amount == null || amount == 0) {
            throw invalidRequest("amount must not be zero");
        }
        validatePositive(operatorId, "operatorId");
        String normalizedRole = requireText(operatorRole, "operatorRole");
        String normalizedReason = requireText(reason, "reason");
        validateLength(normalizedRole, OPERATOR_ROLE_MAX_LENGTH, "operatorRole");
        validateLength(normalizedReason, REASON_MAX_LENGTH, "reason");

        String sourceKey = MANUAL_ADJUSTMENT_SOURCE_PREFIX + adjustmentRequestId;
        StudentPointEvent event = new StudentPointEvent();
        event.setStudentId(studentId);
        event.setSourceType(PointSourceType.MANUAL_ADJUSTMENT);
        event.setSourceId(adjustmentRequestId);
        event.setSourceKey(sourceKey);
        event.setRuleCode(MANUAL_ADJUSTMENT_RULE_CODE);
        event.setRuleName(MANUAL_ADJUSTMENT_RULE_NAME);
        event.setPoints(amount);
        event.setIdempotencyKey(sourceKey + ":" + MANUAL_ADJUSTMENT_RULE_CODE);
        event.setStatus(PointEventStatus.PENDING);
        event.setAutoAttemptCount(0);
        event.setOperatorId(operatorId);
        event.setOperatorRole(normalizedRole);
        event.setReason(normalizedReason);
        return event;
    }

    public boolean matchesManualAdjustment(StudentPointEvent existing, StudentPointEvent expected) {
        return java.util.Objects.equals(existing.getStudentId(), expected.getStudentId())
                && existing.getSourceType() == PointSourceType.MANUAL_ADJUSTMENT
                && java.util.Objects.equals(existing.getSourceId(), expected.getSourceId())
                && java.util.Objects.equals(existing.getSourceKey(), expected.getSourceKey())
                && java.util.Objects.equals(existing.getRuleCode(), expected.getRuleCode())
                && java.util.Objects.equals(existing.getRuleName(), expected.getRuleName())
                && java.util.Objects.equals(existing.getPoints(), expected.getPoints())
                && java.util.Objects.equals(existing.getIdempotencyKey(), expected.getIdempotencyKey())
                && java.util.Objects.equals(existing.getOperatorId(), expected.getOperatorId())
                && java.util.Objects.equals(existing.getOperatorRole(), expected.getOperatorRole())
                && java.util.Objects.equals(existing.getReason(), expected.getReason());
    }

    private void validatePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalidRequest(field + " must be positive");
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw invalidRequest(field + " must not be blank");
        }
        return value.trim();
    }

    private void validateLength(String value, int maxLength, String field) {
        if (value.length() > maxLength) {
            throw new StudentPointOperationException(
                    "POINT_EVENT_FIELD_TOO_LONG",
                    HttpStatus.BAD_REQUEST,
                    field + " must not exceed " + maxLength + " characters"
            );
        }
    }

    private StudentPointOperationException invalidRequest(String message) {
        return new StudentPointOperationException(
                "INVALID_POINT_EVENT_REQUEST",
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
