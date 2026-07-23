package com.example.words.dto;

import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointEvent;
import java.time.LocalDateTime;

public record StudentPointEventResponse(
        Long id,
        Long studentId,
        String studentUsername,
        String studentName,
        PointSourceType sourceType,
        Long sourceId,
        String sourceKey,
        String ruleCode,
        String ruleName,
        Integer points,
        PointEventStatus status,
        Integer autoAttemptCount,
        LocalDateTime nextRetryAt,
        String lastError,
        Long operatorId,
        String operatorRole,
        String reason,
        Long transactionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime processedAt
) {
    public static StudentPointEventResponse from(StudentPointEvent event) {
        return from(event, null, null);
    }

    public static StudentPointEventResponse from(
            StudentPointEvent event,
            String studentName,
            String studentUsername
    ) {
        return new StudentPointEventResponse(
                event.getId(), event.getStudentId(), studentUsername, studentName, event.getSourceType(), event.getSourceId(),
                event.getSourceKey(), event.getRuleCode(), event.getRuleName(), event.getPoints(),
                event.getStatus(), event.getAutoAttemptCount(), event.getNextRetryAt(), event.getLastError(),
                event.getOperatorId(), event.getOperatorRole(), event.getReason(), event.getTransactionId(),
                event.getCreatedAt(), event.getUpdatedAt(), event.getProcessedAt()
        );
    }
}
