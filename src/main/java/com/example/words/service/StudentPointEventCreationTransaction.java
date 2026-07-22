package com.example.words.service;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.StudentPointRule;
import com.example.words.repository.StudentPointEventRepository;
import com.example.words.repository.StudentPointRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentPointEventCreationTransaction {

    private final StudentPointRuleRepository ruleRepository;
    private final StudentPointEventRepository eventRepository;
    private final StudentPointEventFactory eventFactory;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StudentPointEvent createRuleEvent(RuleEventRequest request) {
        StudentPointRule rule = ruleRepository.findByCodeForUpdate(request.ruleCode())
                .orElseThrow(() -> new StudentPointOperationException(
                        "POINT_RULE_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "Point rule not found: " + request.ruleCode()
                ));
        validateRule(rule, request.ruleCode());

        StudentPointEvent event = baseEvent(
                request.studentId(),
                rule.getSourceType(),
                request.sourceId(),
                request.sourceKey(),
                rule.getCode().trim(),
                rule.getName().trim(),
                rule.getBasePoints(),
                request.idempotencyKey(),
                request.operatorId(),
                request.operatorRole(),
                request.reason()
        );
        return eventRepository.saveAndFlush(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StudentPointEvent createManualEvent(ManualEventRequest request) {
        StudentPointEvent event = eventFactory.manualAdjustment(
                request.studentId(),
                request.adjustmentRequestId(),
                request.amount(),
                request.operatorId(),
                request.operatorRole(),
                request.reason()
        );
        return eventRepository.saveAndFlush(event);
    }

    private void validateRule(StudentPointRule rule, String requestedRuleCode) {
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            throw new StudentPointOperationException(
                    "POINT_RULE_DISABLED",
                    HttpStatus.CONFLICT,
                    "Point rule is disabled: " + requestedRuleCode
            );
        }
        if (rule.getBasePoints() == null || rule.getBasePoints() == 0
                || rule.getSourceType() == null || isBlank(rule.getCode()) || isBlank(rule.getName())) {
            throw new StudentPointOperationException(
                    "POINT_RULE_CONFIGURATION_INVALID",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Point rule configuration is invalid: " + requestedRuleCode
            );
        }
    }

    private StudentPointEvent baseEvent(
            Long studentId,
            PointSourceType sourceType,
            Long sourceId,
            String sourceKey,
            String ruleCode,
            String ruleName,
            Integer points,
            String idempotencyKey,
            Long operatorId,
            String operatorRole,
            String reason
    ) {
        StudentPointEvent event = new StudentPointEvent();
        event.setStudentId(studentId);
        event.setSourceType(sourceType);
        event.setSourceId(sourceId);
        event.setSourceKey(sourceKey);
        event.setRuleCode(ruleCode);
        event.setRuleName(ruleName);
        event.setPoints(points);
        event.setIdempotencyKey(idempotencyKey);
        event.setStatus(PointEventStatus.PENDING);
        event.setAutoAttemptCount(0);
        event.setOperatorId(operatorId);
        event.setOperatorRole(operatorRole);
        event.setReason(reason);
        return event;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record RuleEventRequest(
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

    public record ManualEventRequest(
            Long studentId,
            Long adjustmentRequestId,
            Integer amount,
            String sourceKey,
            String idempotencyKey,
            Long operatorId,
            String operatorRole,
            String reason
    ) {
    }
}
