package com.example.words.service;

import com.example.words.model.PointAdjustmentStatus;
import com.example.words.model.PointAttemptTriggerType;
import com.example.words.model.PointEventAttemptStatus;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointAdjustmentRequest;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.StudentPointEventAttempt;
import com.example.words.repository.StudentPointAdjustmentRequestRepository;
import com.example.words.repository.StudentPointEventAttemptRepository;
import com.example.words.repository.StudentPointEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class StudentPointFailureRecorder {

    private static final int ERROR_MAX_LENGTH = 1000;
    private static final String MANUAL_ADJUSTMENT_INVALID = "MANUAL_ADJUSTMENT_STATE_INVALID";

    private final StudentPointEventRepository eventRepository;
    private final StudentPointEventAttemptRepository attemptRepository;
    private final StudentPointAdjustmentRequestRepository adjustmentRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StudentPointEvent recordFailure(Long eventId, RuntimeException failure, Instant failedAt) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        return record(eventId, message, failedAt, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StudentPointEvent recover(Long eventId, String errorCode, Instant failedAt) {
        return record(eventId, errorCode, failedAt, true);
    }

    private StudentPointEvent record(
            Long eventId,
            String errorMessage,
            Instant failedAt,
            boolean immediateRetry
    ) {
        LocalDateTime failed = LocalDateTime.ofInstant(failedAt, clock.getZone());
        StudentPointEvent event = eventRepository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || event.getStatus() != PointEventStatus.PROCESSING) {
            return event;
        }
        String boundedError = truncate(errorMessage, ERROR_MAX_LENGTH);
        if (!failManualAdjustmentIfMatching(event, failed)) {
            boundedError = MANUAL_ADJUSTMENT_INVALID;
        }
        StudentPointEventAttempt attempt = newAttempt(event, boundedError, failed);
        attemptRepository.save(attempt);

        if (event.getProcessingTriggerType() == PointAttemptTriggerType.AUTO) {
            int attemptCount = event.getAutoAttemptCount() + 1;
            event.setAutoAttemptCount(attemptCount);
            if (attemptCount < StudentPointProcessingPolicy.MAX_AUTO_ATTEMPTS) {
                event.setNextRetryAt(immediateRetry ? failed : failed.plusMinutes(attemptCount));
            } else {
                event.setNextRetryAt(null);
            }
        }
        event.setStatus(PointEventStatus.FAILED);
        event.setLastError(boundedError);
        return eventRepository.saveAndFlush(event);
    }

    private StudentPointEventAttempt newAttempt(
            StudentPointEvent event,
            String errorMessage,
            LocalDateTime failed
    ) {
        int attemptNo = attemptRepository.findTopByEventIdOrderByAttemptNoDesc(event.getId())
                .map(attempt -> attempt.getAttemptNo() + 1)
                .orElse(1);
        StudentPointEventAttempt attempt = new StudentPointEventAttempt();
        attempt.setEventId(event.getId());
        attempt.setAttemptNo(attemptNo);
        attempt.setTriggerType(event.getProcessingTriggerType());
        attempt.setStatus(PointEventAttemptStatus.FAILED);
        attempt.setOperatorId(event.getProcessingOperatorId());
        attempt.setOperatorRole(event.getProcessingOperatorRole());
        attempt.setReason(event.getProcessingReason());
        attempt.setErrorMessage(errorMessage);
        attempt.setStartedAt(event.getProcessingStartedAt() == null ? failed : event.getProcessingStartedAt());
        attempt.setFinishedAt(failed);
        return attempt;
    }

    private boolean failManualAdjustmentIfMatching(StudentPointEvent event, LocalDateTime failed) {
        if (event.getSourceType() != PointSourceType.MANUAL_ADJUSTMENT) {
            return true;
        }
        if (!StudentPointManualAdjustmentIdentity.isValidEvent(event)) {
            logInvalidManualAdjustment(event);
            return false;
        }
        StudentPointAdjustmentRequest request = adjustmentRepository.findByIdForUpdate(event.getSourceId())
                .orElse(null);
        if (request == null) {
            logInvalidManualAdjustment(event);
            return false;
        }
        if (!StudentPointManualAdjustmentIdentity.matchesRequest(event, request)) {
            logInvalidManualAdjustment(event);
            return false;
        }
        request.setStatus(PointAdjustmentStatus.FAILED);
        request.setProcessedAt(failed);
        adjustmentRepository.save(request);
        return true;
    }

    private void logInvalidManualAdjustment(StudentPointEvent event) {
        log.error(
                "Invalid manual adjustment linkage for point event {} and request {}",
                event.getId(),
                event.getSourceId()
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "Unknown point processing failure";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
