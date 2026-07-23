package com.example.words.service;

import com.example.words.dto.StudentPointRuleCreateRequest;
import com.example.words.dto.StudentPointRuleUpdateRequest;
import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.AppUser;
import com.example.words.model.PointEventStatus;
import com.example.words.model.StudentPointRule;
import com.example.words.model.StudentPointRuleAudit;
import com.example.words.model.UserRole;
import com.example.words.repository.StudentPointEventRepository;
import com.example.words.repository.StudentPointRuleAuditRepository;
import com.example.words.repository.StudentPointRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumSet;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentPointRuleService {

    private static final String RULE_CODE_CONSTRAINT = "uk_student_point_rules_code";
    private static final String DEFAULT_AUDIT_REASON = "未填写变更原因";
    private static final EnumSet<PointEventStatus> UNFINISHED_STATUSES = EnumSet.of(
            PointEventStatus.PENDING,
            PointEventStatus.PROCESSING,
            PointEventStatus.FAILED
    );

    private final StudentPointRuleRepository ruleRepository;
    private final StudentPointEventRepository eventRepository;
    private final StudentPointRuleAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public StudentPointRule create(AppUser actor, StudentPointRuleCreateRequest request) {
        validateAdmin(actor);
        validatePoints(request.basePoints());
        String code = normalizeCode(request.code());
        if (ruleRepository.findByCode(code).isPresent()) {
            throw error("POINT_RULE_CODE_EXISTS", HttpStatus.CONFLICT, "Point rule code already exists");
        }
        StudentPointRule rule = StudentPointRule.create(code, request.name().trim(), request.sourceType(),
                request.basePoints());
        rule.setDescription(normalizeNullable(request.description()));
        rule.setScopeType(normalizeScope(request.scopeType()));
        rule.setScopeId(request.scopeId());
        rule.setEnabled(request.enabled() == null || request.enabled());
        try {
            StudentPointRule saved = ruleRepository.saveAndFlush(rule);
            saveAudit(saved, "CREATE", actor, request.reason(), null, snapshot(saved));
            return saved;
        } catch (DataIntegrityViolationException failure) {
            if (isRuleCodeConstraint(failure)) {
                throw error("POINT_RULE_CODE_EXISTS", HttpStatus.CONFLICT, "Point rule code already exists");
            }
            throw failure;
        }
    }

    @Transactional
    public StudentPointRule update(AppUser actor, Long ruleId, StudentPointRuleUpdateRequest request) {
        validateAdmin(actor);
        validatePoints(request.basePoints());
        StudentPointRule rule = ruleRepository.findByIdForUpdate(ruleId)
                .orElseThrow(() -> error("POINT_RULE_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Point rule does not exist"));
        if (eventRepository.existsByRuleCodeAndStatusIn(rule.getCode(), UNFINISHED_STATUSES)) {
            throw error("POINT_RULE_HAS_UNFINISHED_EVENTS", HttpStatus.CONFLICT,
                    "Point rule has unfinished events; process or cancel them before editing the rule");
        }
        RuleSnapshot before = snapshot(rule);
        rule.setName(request.name().trim());
        rule.setDescription(normalizeNullable(request.description()));
        rule.setSourceType(request.sourceType());
        rule.setBasePoints(request.basePoints());
        rule.setScopeType(normalizeScope(request.scopeType()));
        rule.setScopeId(request.scopeId());
        rule.setEnabled(request.enabled());
        StudentPointRule saved = ruleRepository.save(rule);
        saveAudit(saved, "UPDATE", actor, request.reason(), before, snapshot(saved));
        return saved;
    }

    private void saveAudit(
            StudentPointRule rule,
            String action,
            AppUser actor,
            String reason,
            RuleSnapshot before,
            RuleSnapshot after
    ) {
        StudentPointRuleAudit audit = new StudentPointRuleAudit();
        audit.setRuleId(rule.getId());
        audit.setRuleCode(rule.getCode());
        audit.setAction(action);
        audit.setOperatorId(actor.getId());
        audit.setOperatorRole(actor.getRole().name());
        audit.setReason(normalizeReason(reason));
        audit.setBeforeSnapshot(toJson(before));
        audit.setAfterSnapshot(toJson(after));
        auditRepository.save(audit);
    }

    private RuleSnapshot snapshot(StudentPointRule rule) {
        return new RuleSnapshot(
                rule.getCode(), rule.getName(), rule.getDescription(), rule.getSourceType(), rule.getBasePoints(),
                rule.getScopeType(), rule.getScopeId(), rule.getEnabled()
        );
    }

    private String toJson(RuleSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Unable to serialize point rule audit snapshot", failure);
        }
    }

    private void validateAdmin(AppUser actor) {
        if (actor == null || actor.getId() == null || actor.getRole() != UserRole.ADMIN) {
            throw error("POINT_ADMIN_REQUIRED", HttpStatus.FORBIDDEN, "Administrator is required");
        }
    }

    private void validatePoints(Integer points) {
        if (points == null || points == 0) {
            throw error("INVALID_POINT_RULE_POINTS", HttpStatus.BAD_REQUEST,
                    "Point rule base points must not be zero");
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeScope(String scopeType) {
        return scopeType == null || scopeType.isBlank() ? "GLOBAL" : scopeType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeReason(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_AUDIT_REASON;
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw error("POINT_RULE_REASON_TOO_LONG", HttpStatus.BAD_REQUEST,
                    "Rule change reason must not exceed 500 characters");
        }
        return normalized;
    }

    private boolean isRuleCodeConstraint(DataIntegrityViolationException failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && RULE_CODE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(RULE_CODE_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private StudentPointOperationException error(String code, HttpStatus status, String message) {
        return new StudentPointOperationException(code, status, message);
    }

    private record RuleSnapshot(
            String code,
            String name,
            String description,
            com.example.words.model.PointSourceType sourceType,
            Integer basePoints,
            String scopeType,
            Long scopeId,
            Boolean enabled
    ) {
    }
}
