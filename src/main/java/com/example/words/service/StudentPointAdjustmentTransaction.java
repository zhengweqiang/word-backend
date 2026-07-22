package com.example.words.service;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.AppUser;
import com.example.words.model.PointAdjustmentStatus;
import com.example.words.model.PointAttemptTriggerType;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointAdjustmentRequest;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.UserRole;
import com.example.words.repository.StudentPointAdjustmentRequestRepository;
import com.example.words.repository.StudentPointEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentPointAdjustmentTransaction {

    private static final int EVENT_REASON_MAX_LENGTH = 500;

    private final StudentPointAdjustmentRequestRepository requestRepository;
    private final StudentPointEventRepository eventRepository;
    private final StudentPointEventFactory eventFactory;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Workflow createWorkflow(CreateCommand command) {
        if (command.replacesRequestId() == null) {
            return createInitialWorkflow(command);
        }
        return createReplacementWorkflow(command);
    }

    private Workflow createInitialWorkflow(CreateCommand command) {
        StudentPointAdjustmentRequest request = newRequest(command);
        request = requestRepository.saveAndFlush(request);
        StudentPointEvent event = eventRepository.saveAndFlush(newEvent(request, command));
        return new Workflow(request, event);
    }

    private Workflow createReplacementWorkflow(CreateCommand command) {
        StudentPointEvent oldEvent = eventRepository.findBySourceTypeAndSourceIdForUpdate(
                        PointSourceType.MANUAL_ADJUSTMENT,
                        command.replacesRequestId()
                )
                .orElseThrow(() -> conflict(
                        "POINT_ADJUSTMENT_EVENT_NOT_FOUND",
                        "Old manual adjustment event does not exist"
                ));
        StudentPointAdjustmentRequest oldRequest = lockRequest(command.replacesRequestId());
        validateReplacement(oldRequest, oldEvent, command.studentId(), command.actor());

        StudentPointAdjustmentRequest request = requestRepository.saveAndFlush(newRequest(command));
        LocalDateTime now = LocalDateTime.now(clock);
        String auditReason = replacementAuditReason(request.getId(), command.actor(), command.reason());
        cancelOldEvent(oldEvent, command.actor(), auditReason, now);
        oldRequest.setStatus(PointAdjustmentStatus.REJECTED);
        oldRequest.setProcessedAt(now);
        oldRequest.setReplacedByRequestId(request.getId());
        request.setReplacesRequestId(oldRequest.getId());
        requestRepository.save(oldRequest);
        requestRepository.save(request);

        StudentPointEvent event = eventRepository.saveAndFlush(newEvent(request, command));
        return new Workflow(request, event);
    }

    private StudentPointAdjustmentRequest newRequest(CreateCommand command) {
        return StudentPointAdjustmentRequest.create(
                command.requestKey(),
                command.studentId(),
                command.amount(),
                command.reason(),
                command.actor().getId(),
                command.actor().getRole().name(),
                command.replacesRequestId()
        );
    }

    private StudentPointEvent newEvent(StudentPointAdjustmentRequest request, CreateCommand command) {
        return eventFactory.manualAdjustment(
                request.getStudentId(),
                request.getId(),
                request.getAmount(),
                command.actor().getId(),
                command.actor().getRole().name(),
                request.getReason()
        );
    }

    private void cancelOldEvent(
            StudentPointEvent oldEvent,
            AppUser actor,
            String auditReason,
            LocalDateTime now
    ) {
        oldEvent.setStatus(PointEventStatus.CANCELLED);
        oldEvent.setProcessingTriggerType(PointAttemptTriggerType.MANUAL);
        oldEvent.setProcessingOperatorId(actor.getId());
        oldEvent.setProcessingOperatorRole(actor.getRole().name());
        oldEvent.setProcessingReason(auditReason);
        oldEvent.setProcessingStartedAt(now);
        oldEvent.setReason(auditReason);
        oldEvent.setProcessedAt(now);
        oldEvent.setNextRetryAt(null);
    }

    private String replacementAuditReason(Long newRequestId, AppUser actor, String reason) {
        String prefix = "Replaced by adjustment " + newRequestId
                + " by " + actor.getRole().name() + "#" + actor.getId() + ": ";
        int remainingLength = EVENT_REASON_MAX_LENGTH - prefix.length();
        String userText = reason == null ? "" : reason;
        if (userText.length() > remainingLength) {
            userText = userText.substring(0, remainingLength);
        }
        return prefix + userText;
    }

    private void validateReplacement(
            StudentPointAdjustmentRequest oldRequest,
            StudentPointEvent oldEvent,
            Long studentId,
            AppUser actor
    ) {
        if (!oldRequest.getStudentId().equals(studentId) || !oldEvent.getStudentId().equals(studentId)) {
            throw conflict("POINT_ADJUSTMENT_STUDENT_MISMATCH", "Replacement must belong to the same student");
        }
        if (actor.getRole() == UserRole.TEACHER && !oldRequest.getRequestedBy().equals(actor.getId())) {
            throw new StudentPointOperationException(
                    "POINT_ADJUSTMENT_NOT_OWNED",
                    HttpStatus.FORBIDDEN,
                    "Teacher can replace only their own adjustment"
            );
        }
        if (oldEvent.getStatus() == PointEventStatus.SUCCEEDED
                || oldRequest.getStatus() == PointAdjustmentStatus.APPLIED
                || oldRequest.getStatus() == PointAdjustmentStatus.REVERSED) {
            throw conflict("POINT_ADJUSTMENT_ALREADY_APPLIED", "Applied adjustment must be reversed");
        }
        if (oldEvent.getStatus() == PointEventStatus.CANCELLED
                || oldRequest.getStatus() == PointAdjustmentStatus.REJECTED) {
            throw conflict("POINT_ADJUSTMENT_ALREADY_REPLACED", "Adjustment was already replaced");
        }
        if (oldEvent.getStatus() == PointEventStatus.PENDING
                || oldEvent.getStatus() == PointEventStatus.PROCESSING
                || (oldEvent.getStatus() == PointEventStatus.FAILED
                && oldEvent.getAutoAttemptCount() < StudentPointProcessingPolicy.MAX_AUTO_ATTEMPTS)) {
            throw conflict("POINT_ADJUSTMENT_RETRY_IN_PROGRESS", "Automatic retries are not finished");
        }
        if (!StudentPointManualAdjustmentIdentity.isValidEvent(oldEvent)
                || !StudentPointManualAdjustmentIdentity.matchesRequest(oldEvent, oldRequest)) {
            throw conflict(
                    "MANUAL_ADJUSTMENT_STATE_INVALID",
                    "Old manual adjustment request does not match its event"
            );
        }
        if (oldEvent.getStatus() != PointEventStatus.FAILED
                || oldEvent.getAutoAttemptCount() < StudentPointProcessingPolicy.MAX_AUTO_ATTEMPTS
                || oldRequest.getStatus() != PointAdjustmentStatus.FAILED) {
            throw conflict("POINT_ADJUSTMENT_REPLACEMENT_INVALID", "Adjustment cannot be replaced");
        }
    }

    private StudentPointAdjustmentRequest lockRequest(Long requestId) {
        return requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new StudentPointOperationException(
                        "POINT_ADJUSTMENT_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "Point adjustment request does not exist"
                ));
    }

    private StudentPointOperationException conflict(String code, String message) {
        return new StudentPointOperationException(code, HttpStatus.CONFLICT, message);
    }

    public record CreateCommand(
            String requestKey,
            Long studentId,
            Integer amount,
            String reason,
            AppUser actor,
            Long replacesRequestId
    ) {
    }

    public record Workflow(
            StudentPointAdjustmentRequest request,
            StudentPointEvent event
    ) {
    }
}
