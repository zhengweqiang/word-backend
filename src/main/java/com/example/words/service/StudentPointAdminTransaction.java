package com.example.words.service;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.AppUser;
import com.example.words.model.PointAdjustmentStatus;
import com.example.words.model.PointAttemptTriggerType;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointAdjustmentRequest;
import com.example.words.model.StudentPointEvent;
import com.example.words.repository.StudentPointAdjustmentRequestRepository;
import com.example.words.repository.StudentPointEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentPointAdminTransaction {

    private final StudentPointEventRepository eventRepository;
    private final StudentPointAdjustmentRequestRepository adjustmentRepository;
    private final Clock clock;

    @Transactional
    public StudentPointEvent cancelEvent(Long eventId, AppUser actor, String reason) {
        StudentPointEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> error("POINT_EVENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Point event does not exist"));
        if (event.getStatus() == PointEventStatus.CANCELLED) {
            return event;
        }
        if (event.getStatus() == PointEventStatus.PROCESSING) {
            throw error("POINT_EVENT_PROCESSING", HttpStatus.CONFLICT, "Point event is processing");
        }
        if (event.getStatus() == PointEventStatus.SUCCEEDED) {
            throw error("POINT_EVENT_REVERSAL_REQUIRED", HttpStatus.CONFLICT,
                    "Succeeded point event must be reversed");
        }
        if (event.getStatus() != PointEventStatus.PENDING && event.getStatus() != PointEventStatus.FAILED) {
            throw error("POINT_EVENT_STATE_CONFLICT", HttpStatus.CONFLICT, "Point event cannot be cancelled");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (event.getSourceType() == PointSourceType.MANUAL_ADJUSTMENT) {
            rejectManualAdjustment(event, now);
        }
        event.setStatus(PointEventStatus.CANCELLED);
        event.setProcessingTriggerType(PointAttemptTriggerType.MANUAL);
        event.setProcessingOperatorId(actor.getId());
        event.setProcessingOperatorRole(actor.getRole().name());
        event.setProcessingReason(reason);
        event.setProcessingStartedAt(now);
        event.setProcessedAt(now);
        event.setNextRetryAt(null);
        return eventRepository.saveAndFlush(event);
    }

    private void rejectManualAdjustment(StudentPointEvent event, LocalDateTime now) {
        if (!StudentPointManualAdjustmentIdentity.isValidEvent(event)) {
            throw error("MANUAL_ADJUSTMENT_STATE_INVALID", HttpStatus.CONFLICT,
                    "Manual adjustment event linkage is invalid");
        }
        StudentPointAdjustmentRequest request = adjustmentRepository.findByIdForUpdate(event.getSourceId())
                .orElseThrow(() -> error("MANUAL_ADJUSTMENT_STATE_INVALID", HttpStatus.CONFLICT,
                        "Manual adjustment request does not exist"));
        if (request.getStatus() == PointAdjustmentStatus.APPLIED
                || request.getStatus() == PointAdjustmentStatus.REVERSED) {
            throw error("POINT_ADJUSTMENT_ALREADY_APPLIED", HttpStatus.CONFLICT,
                    "Applied adjustment cannot be cancelled");
        }
        if (!StudentPointManualAdjustmentIdentity.matchesRequest(event, request)) {
            throw error("MANUAL_ADJUSTMENT_STATE_INVALID", HttpStatus.CONFLICT,
                    "Manual adjustment request does not match event");
        }
        request.setStatus(PointAdjustmentStatus.REJECTED);
        request.setProcessedAt(now);
        adjustmentRepository.saveAndFlush(request);
    }

    private StudentPointOperationException error(String code, HttpStatus status, String message) {
        return new StudentPointOperationException(code, status, message);
    }
}
