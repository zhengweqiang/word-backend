package com.example.words.service;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.PointAdjustmentStatus;
import com.example.words.model.PointEventAttemptStatus;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointAdjustmentRequest;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.StudentPointEventAttempt;
import com.example.words.model.StudentPointTransaction;
import com.example.words.repository.StudentPointAdjustmentRequestRepository;
import com.example.words.repository.StudentPointEventAttemptRepository;
import com.example.words.repository.StudentPointEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentPointPostingTransaction {

    private final StudentPointEventRepository eventRepository;
    private final StudentPointEventAttemptRepository attemptRepository;
    private final StudentPointAdjustmentRequestRepository adjustmentRepository;
    private final StudentPointLedgerService ledgerService;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StudentPointEvent post(Long eventId, Instant finishedAt) {
        LocalDateTime finished = LocalDateTime.ofInstant(finishedAt, clock.getZone());
        StudentPointEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> error("POINT_EVENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "积分事件不存在: " + eventId));
        if (event.getStatus() != PointEventStatus.PROCESSING) {
            throw error("POINT_EVENT_NOT_PROCESSING", HttpStatus.CONFLICT, "积分事件不在处理状态");
        }

        StudentPointTransaction transaction = ledgerService.post(new StudentPointLedgerService.PostRequest(
                event.getStudentId(),
                event.getPoints(),
                event.getSourceType(),
                event.getSourceId(),
                event.getSourceKey(),
                event.getRuleCode(),
                event.getIdempotencyKey(),
                new StudentPointLedgerService.Actor(event.getOperatorId(), event.getOperatorRole()),
                event.getReason()
        ));

        closeManualAdjustment(event, transaction, finished);

        StudentPointEventAttempt attempt = newAttempt(event, PointEventAttemptStatus.SUCCEEDED, finished);
        attemptRepository.save(attempt);

        event.setStatus(PointEventStatus.SUCCEEDED);
        event.setTransactionId(transaction.getId());
        event.setProcessedAt(finished);
        event.setLastError(null);
        event.setNextRetryAt(null);
        return eventRepository.saveAndFlush(event);
    }

    private void closeManualAdjustment(
            StudentPointEvent event,
            StudentPointTransaction transaction,
            LocalDateTime finished
    ) {
        if (event.getSourceType() != PointSourceType.MANUAL_ADJUSTMENT) {
            return;
        }
        if (!StudentPointManualAdjustmentIdentity.isValidEvent(event)) {
            throw invalidManualAdjustment();
        }
        StudentPointAdjustmentRequest request = adjustmentRepository.findByIdForUpdate(event.getSourceId())
                .orElseThrow(this::invalidManualAdjustment);
        if (!StudentPointManualAdjustmentIdentity.matchesRequest(event, request)) {
            throw invalidManualAdjustment();
        }
        request.setStatus(PointAdjustmentStatus.APPLIED);
        request.setTransactionId(transaction.getId());
        request.setProcessedAt(finished);
        adjustmentRepository.save(request);
    }

    private StudentPointEventAttempt newAttempt(
            StudentPointEvent event,
            PointEventAttemptStatus status,
            LocalDateTime finished
    ) {
        int attemptNo = attemptRepository.findTopByEventIdOrderByAttemptNoDesc(event.getId())
                .map(attempt -> attempt.getAttemptNo() + 1)
                .orElse(1);
        StudentPointEventAttempt attempt = new StudentPointEventAttempt();
        attempt.setEventId(event.getId());
        attempt.setAttemptNo(attemptNo);
        attempt.setTriggerType(event.getProcessingTriggerType());
        attempt.setStatus(status);
        attempt.setOperatorId(event.getProcessingOperatorId());
        attempt.setOperatorRole(event.getProcessingOperatorRole());
        attempt.setReason(event.getProcessingReason());
        attempt.setStartedAt(event.getProcessingStartedAt());
        attempt.setFinishedAt(finished);
        return attempt;
    }

    private StudentPointOperationException invalidManualAdjustment() {
        return error(
                "MANUAL_ADJUSTMENT_STATE_INVALID",
                HttpStatus.CONFLICT,
                "手工积分调整单与积分事件不一致"
        );
    }

    private StudentPointOperationException error(String code, HttpStatus status, String message) {
        return new StudentPointOperationException(code, status, message);
    }
}
