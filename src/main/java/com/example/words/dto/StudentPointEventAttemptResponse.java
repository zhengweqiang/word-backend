package com.example.words.dto;

import com.example.words.model.PointAttemptTriggerType;
import com.example.words.model.PointEventAttemptStatus;
import com.example.words.model.StudentPointEventAttempt;
import java.time.LocalDateTime;

public record StudentPointEventAttemptResponse(
        Long id,
        Long eventId,
        Integer attemptNo,
        PointAttemptTriggerType triggerType,
        PointEventAttemptStatus status,
        Long operatorId,
        String operatorRole,
        String reason,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
    public static StudentPointEventAttemptResponse from(StudentPointEventAttempt attempt) {
        return new StudentPointEventAttemptResponse(
                attempt.getId(), attempt.getEventId(), attempt.getAttemptNo(), attempt.getTriggerType(),
                attempt.getStatus(), attempt.getOperatorId(), attempt.getOperatorRole(), attempt.getReason(),
                attempt.getErrorMessage(), attempt.getStartedAt(), attempt.getFinishedAt()
        );
    }
}
