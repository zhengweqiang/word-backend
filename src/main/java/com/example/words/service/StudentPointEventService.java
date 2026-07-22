package com.example.words.service;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.PointAttemptTriggerType;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointEvent;
import com.example.words.repository.StudentPointEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentPointEventService {

    private static final String EVENT_IDEMPOTENCY_CONSTRAINT = "uk_student_point_events_idempotency";
    private static final int SOURCE_KEY_MAX_LENGTH = 200;
    private static final int RULE_CODE_MAX_LENGTH = 64;
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 160;
    private static final int OPERATOR_ROLE_MAX_LENGTH = 32;
    private static final int REASON_MAX_LENGTH = 500;

    private final StudentPointEventRepository eventRepository;
    private final StudentPointEventCreationTransaction creationTransaction;
    private final StudentPointEventFactory eventFactory;
    private final Clock clock;

    public StudentPointEvent create(CreateRequest request) {
        OrdinaryIdentity identity = normalizeOrdinary(request);
        StudentPointEvent existing = eventRepository.findByIdempotencyKey(identity.idempotencyKey())
                .orElse(null);
        if (existing != null) {
            return requireMatchingOrdinaryIdentity(existing, identity);
        }

        StudentPointEventCreationTransaction.RuleEventRequest transactionRequest =
                new StudentPointEventCreationTransaction.RuleEventRequest(
                        identity.studentId(),
                        identity.sourceId(),
                        identity.sourceKey(),
                        identity.ruleCode(),
                        identity.idempotencyKey(),
                        identity.operatorId(),
                        identity.operatorRole(),
                        identity.reason()
                );
        try {
            return creationTransaction.createRuleEvent(transactionRequest);
        } catch (DataIntegrityViolationException exception) {
            return recoverOrdinaryUniqueRace(exception, identity);
        }
    }

    public StudentPointEvent createManualAdjustment(
            Long studentId,
            Long adjustmentRequestId,
            Integer amount,
            Actor actor,
            String reason
    ) {
        StudentPointEvent expected = eventFactory.manualAdjustment(
                studentId,
                adjustmentRequestId,
                amount,
                actor == null ? null : actor.operatorId(),
                actor == null ? null : actor.operatorRole(),
                reason
        );
        StudentPointEvent existing = eventRepository.findByIdempotencyKey(expected.getIdempotencyKey())
                .orElse(null);
        if (existing != null) {
            return requireMatchingManualIdentity(existing, expected);
        }

        StudentPointEventCreationTransaction.ManualEventRequest transactionRequest =
                new StudentPointEventCreationTransaction.ManualEventRequest(
                        expected.getStudentId(),
                        expected.getSourceId(),
                        expected.getPoints(),
                        expected.getSourceKey(),
                        expected.getIdempotencyKey(),
                        expected.getOperatorId(),
                        expected.getOperatorRole(),
                        expected.getReason()
                );
        try {
            return creationTransaction.createManualEvent(transactionRequest);
        } catch (DataIntegrityViolationException exception) {
            return recoverManualUniqueRace(exception, expected);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StudentPointEvent claim(Long eventId, AttemptContext context, Instant startedAt) {
        validatePositive(eventId, "eventId");
        if (context == null || context.triggerType() == null || startedAt == null) {
            throw invalidRequest("attempt context and startedAt are required");
        }
        AttemptContext normalized = normalizeAttemptContext(context);
        LocalDateTime processingStartedAt = LocalDateTime.ofInstant(startedAt, clock.getZone());
        int affected = eventRepository.claimForProcessing(
                eventId,
                normalized.triggerType(),
                normalized.operatorId(),
                normalized.operatorRole(),
                normalized.reason(),
                processingStartedAt,
                normalized.triggerType() == PointAttemptTriggerType.AUTO,
                StudentPointProcessingPolicy.MAX_AUTO_ATTEMPTS
        );
        if (affected == 1) {
            return eventRepository.findById(eventId).orElseThrow(() -> eventNotFound(eventId));
        }
        StudentPointEvent current = eventRepository.findById(eventId)
                .orElseThrow(() -> eventNotFound(eventId));
        return resolveFailedClaim(current, normalized.triggerType());
    }

    private StudentPointEvent resolveFailedClaim(
            StudentPointEvent event,
            PointAttemptTriggerType triggerType
    ) {
        if (event.getStatus() == PointEventStatus.SUCCEEDED) {
            return event;
        }
        if (event.getStatus() == PointEventStatus.PROCESSING) {
            throw stateConflict("POINT_EVENT_PROCESSING", "积分事件正在处理");
        }
        if (event.getStatus() == PointEventStatus.CANCELLED) {
            throw stateConflict("POINT_EVENT_CANCELLED", "积分事件已取消");
        }
        if (triggerType == PointAttemptTriggerType.AUTO
                && event.getAutoAttemptCount() >= StudentPointProcessingPolicy.MAX_AUTO_ATTEMPTS) {
            throw stateConflict("POINT_EVENT_AUTO_RETRY_EXHAUSTED", "积分事件自动重试次数已用完");
        }
        throw stateConflict("POINT_EVENT_STATE_CONFLICT", "积分事件当前状态不允许处理");
    }

    private AttemptContext normalizeAttemptContext(AttemptContext context) {
        String role = normalizeOptionalText(context.operatorRole());
        String reason = normalizeOptionalText(context.reason());
        if (context.triggerType() == PointAttemptTriggerType.MANUAL) {
            validatePositive(context.operatorId(), "operatorId");
            role = requireText(role, "operatorRole");
            reason = requireText(reason, "reason");
        } else if (context.operatorId() != null && context.operatorId() <= 0) {
            throw invalidRequest("operatorId must be positive");
        }
        validateLength(role, OPERATOR_ROLE_MAX_LENGTH, "operatorRole");
        validateLength(reason, REASON_MAX_LENGTH, "reason");
        return new AttemptContext(context.triggerType(), context.operatorId(), role, reason);
    }

    private StudentPointOperationException eventNotFound(Long eventId) {
        return new StudentPointOperationException(
                "POINT_EVENT_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                "积分事件不存在: " + eventId
        );
    }

    private StudentPointOperationException stateConflict(String code, String message) {
        return new StudentPointOperationException(code, HttpStatus.CONFLICT, message);
    }

    private StudentPointEvent recoverOrdinaryUniqueRace(
            DataIntegrityViolationException exception,
            OrdinaryIdentity identity
    ) {
        requireNamedIdempotencyConstraint(exception);
        StudentPointEvent winner = eventRepository.findByIdempotencyKey(identity.idempotencyKey())
                .orElseThrow(() -> exception);
        return requireMatchingOrdinaryIdentity(winner, identity);
    }

    private StudentPointEvent recoverManualUniqueRace(
            DataIntegrityViolationException exception,
            StudentPointEvent expected
    ) {
        requireNamedIdempotencyConstraint(exception);
        StudentPointEvent winner = eventRepository.findByIdempotencyKey(expected.getIdempotencyKey())
                .orElseThrow(() -> exception);
        return requireMatchingManualIdentity(winner, expected);
    }

    private void requireNamedIdempotencyConstraint(DataIntegrityViolationException exception) {
        if (!isEventIdempotencyConstraint(exception)) {
            throw exception;
        }
    }

    private StudentPointEvent requireMatchingOrdinaryIdentity(
            StudentPointEvent existing,
            OrdinaryIdentity identity
    ) {
        if (Objects.equals(existing.getStudentId(), identity.studentId())
                && Objects.equals(existing.getSourceId(), identity.sourceId())
                && Objects.equals(existing.getSourceKey(), identity.sourceKey())
                && Objects.equals(existing.getRuleCode(), identity.ruleCode())
                && Objects.equals(existing.getIdempotencyKey(), identity.idempotencyKey())) {
            return existing;
        }
        throw idempotencyConflict();
    }

    private StudentPointEvent requireMatchingManualIdentity(
            StudentPointEvent existing,
            StudentPointEvent expected
    ) {
        if (eventFactory.matchesManualAdjustment(existing, expected)) {
            return existing;
        }
        throw idempotencyConflict();
    }

    private OrdinaryIdentity normalizeOrdinary(CreateRequest request) {
        if (request == null) {
            throw invalidRequest("request is required");
        }
        validatePositive(request.studentId(), "studentId");
        String sourceKey = requireText(request.sourceKey(), "sourceKey");
        String ruleCode = requireText(request.ruleCode(), "ruleCode");
        validateLength(sourceKey, SOURCE_KEY_MAX_LENGTH, "sourceKey");
        validateLength(ruleCode, RULE_CODE_MAX_LENGTH, "ruleCode");
        String idempotencyKey = sourceKey + ":" + ruleCode;
        validateLength(idempotencyKey, IDEMPOTENCY_KEY_MAX_LENGTH, "idempotencyKey");
        NormalizedActor actor = normalizeOptionalActor(request.actor());
        String reason = normalizeOptionalText(request.reason());
        validateLength(reason, REASON_MAX_LENGTH, "reason");
        return new OrdinaryIdentity(
                request.studentId(),
                request.sourceId(),
                sourceKey,
                ruleCode,
                idempotencyKey,
                actor == null ? null : actor.operatorId(),
                actor == null ? null : actor.operatorRole(),
                reason
        );
    }

    private NormalizedActor normalizeOptionalActor(Actor actor) {
        if (actor == null) {
            return null;
        }
        if (actor.operatorId() != null && actor.operatorId() <= 0) {
            throw invalidRequest("actor operatorId must be positive");
        }
        String role = normalizeOptionalText(actor.operatorRole());
        if (actor.operatorRole() != null && role == null) {
            throw invalidRequest("actor operatorRole must not be blank");
        }
        validateLength(role, OPERATOR_ROLE_MAX_LENGTH, "operatorRole");
        return new NormalizedActor(actor.operatorId(), role);
    }

    private void validatePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalidRequest(field + " must be positive");
        }
    }

    private String requireText(String value, String field) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw invalidRequest(field + " must not be blank");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateLength(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
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

    private StudentPointOperationException idempotencyConflict() {
        return new StudentPointOperationException(
                "IDEMPOTENCY_KEY_CONFLICT",
                HttpStatus.CONFLICT,
                "Point event idempotency key is already used by a different request"
        );
    }

    private boolean isEventIdempotencyConstraint(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && EVENT_IDEMPOTENCY_CONSTRAINT.equalsIgnoreCase(constraintViolation.getConstraintName())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null
                    && message.toLowerCase(Locale.ROOT).contains(EVENT_IDEMPOTENCY_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public record CreateRequest(
            Long studentId,
            Long sourceId,
            String sourceKey,
            String ruleCode,
            Actor actor,
            String reason
    ) {
    }

    public record Actor(Long operatorId, String operatorRole) {
    }

    public record AttemptContext(
            PointAttemptTriggerType triggerType,
            Long operatorId,
            String operatorRole,
            String reason
    ) {
        public static AttemptContext auto() {
            return new AttemptContext(PointAttemptTriggerType.AUTO, null, null, null);
        }

        public static AttemptContext manual(Long operatorId, String operatorRole, String reason) {
            return new AttemptContext(PointAttemptTriggerType.MANUAL, operatorId, operatorRole, reason);
        }
    }

    private record OrdinaryIdentity(
            Long studentId,
            Long sourceId,
            String sourceKey,
            String ruleCode,
            String idempotencyKey,
            Long operatorId,
            String operatorRole,
            String reason
    ) {
    }

    private record NormalizedActor(Long operatorId, String operatorRole) {
    }
}
