package com.example.words.service;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.AppUser;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.StudentPointRule;
import com.example.words.model.StudentPointTransaction;
import com.example.words.model.UserRole;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.StudentPointRuleRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentPointAdminService {

    private static final int REQUEST_KEY_MAX_LENGTH = 64;
    private static final int REASON_MAX_LENGTH = 500;
    private static final String REDEMPTION_RULE_CODE = "REDEMPTION";
    private static final String REDEMPTION_SOURCE_PREFIX = "redemption:";

    private final StudentPointEventProcessor processor;
    private final StudentPointAdminTransaction adminTransaction;
    private final StudentPointLedgerService ledgerService;
    private final AppUserRepository userRepository;
    private final StudentPointRuleRepository ruleRepository;

    public StudentPointEvent retryEvent(AppUser actor, Long eventId, String reason) {
        String normalizedReason = validate(actor, eventId, reason);
        return processor.process(
                eventId,
                StudentPointEventService.AttemptContext.manual(
                        actor.getId(),
                        actor.getRole().name(),
                        normalizedReason
                )
        );
    }

    public StudentPointEvent cancelEvent(AppUser actor, Long eventId, String reason) {
        return adminTransaction.cancelEvent(eventId, actor, validate(actor, eventId, reason));
    }

    public StudentPointTransaction reverseTransaction(AppUser actor, Long transactionId, String reason) {
        String normalizedReason = validate(actor, transactionId, reason);
        return ledgerService.reverse(
                transactionId,
                new StudentPointLedgerService.Actor(actor.getId(), actor.getRole().name()),
                normalizedReason
        );
    }

    public StudentPointTransaction redeemPoints(
            AppUser actor,
            Long studentId,
            String requestKey,
            BigDecimal points,
            String reason
    ) {
        String normalizedReason = validate(actor, studentId, reason);
        String normalizedRequestKey = normalizeRequestKey(requestKey);
        validateRedemptionPoints(points);
        validateStudent(studentId);
        validateRedemptionRuleMinimum(points);

        String sourceKey = REDEMPTION_SOURCE_PREFIX + normalizedRequestKey;
        return ledgerService.post(new StudentPointLedgerService.PostRequest(
                studentId,
                points.negate(),
                PointSourceType.REDEMPTION,
                null,
                sourceKey,
                REDEMPTION_RULE_CODE,
                sourceKey,
                new StudentPointLedgerService.Actor(actor.getId(), actor.getRole().name()),
                normalizedReason
        ));
    }

    public StudentPointTransaction redeemPoints(
            AppUser actor,
            Long studentId,
            String requestKey,
            int points,
            String reason
    ) {
        return redeemPoints(actor, studentId, requestKey, BigDecimal.valueOf(points), reason);
    }

    private String validate(AppUser actor, Long targetId, String reason) {
        if (actor == null || actor.getId() == null || actor.getRole() != UserRole.ADMIN) {
            throw error("POINT_ADMIN_REQUIRED", HttpStatus.FORBIDDEN, "Administrator is required");
        }
        if (targetId == null || targetId <= 0) {
            throw error("POINT_ADMIN_TARGET_ID_INVALID", HttpStatus.BAD_REQUEST, "Target ID is invalid");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw error("POINT_ADMIN_REASON_REQUIRED", HttpStatus.BAD_REQUEST, "Reason is required");
        }
        String normalized = reason.trim();
        if (normalized.length() > REASON_MAX_LENGTH) {
            throw error("POINT_ADMIN_REASON_TOO_LONG", HttpStatus.BAD_REQUEST,
                    "Reason must not exceed 500 characters");
        }
        return normalized;
    }

    private String normalizeRequestKey(String requestKey) {
        if (requestKey == null || requestKey.trim().isEmpty()) {
            throw error("POINT_REDEMPTION_REQUEST_KEY_REQUIRED", HttpStatus.BAD_REQUEST,
                    "Redemption request key is required");
        }
        String normalized = requestKey.trim();
        if (normalized.length() > REQUEST_KEY_MAX_LENGTH) {
            throw error("POINT_REDEMPTION_REQUEST_KEY_TOO_LONG", HttpStatus.BAD_REQUEST,
                    "Redemption request key must not exceed 64 characters");
        }
        return normalized;
    }

    private void validateRedemptionPoints(BigDecimal points) {
        if (points == null || points.compareTo(BigDecimal.ZERO) <= 0) {
            throw error("INVALID_POINT_REDEMPTION_POINTS", HttpStatus.BAD_REQUEST,
                    "Redemption points must be positive");
        }
    }

    private void validateRedemptionRuleMinimum(BigDecimal points) {
        StudentPointRule rule = ruleRepository.findByCode(REDEMPTION_RULE_CODE)
                .orElseThrow(() -> error(
                        "POINT_REDEMPTION_RULE_INVALID",
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Redemption point rule does not exist"
                ));
        if (!Boolean.TRUE.equals(rule.getEnabled())
                || rule.getBasePoints() == null
                || rule.getBasePoints().compareTo(BigDecimal.ZERO) <= 0) {
            throw error(
                    "POINT_REDEMPTION_RULE_INVALID",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Redemption point rule is invalid"
            );
        }
        if (points.compareTo(rule.getBasePoints()) < 0) {
            throw error(
                    "POINT_REDEMPTION_POINTS_BELOW_RULE",
                    HttpStatus.BAD_REQUEST,
                    "兑换积分数不能小于兑换规则基础分值"
            );
        }
    }

    private void validateStudent(Long studentId) {
        AppUser target = userRepository.findById(studentId)
                .orElseThrow(() -> error(
                        "POINT_STUDENT_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "Target student does not exist"
                ));
        if (target.getRole() != UserRole.STUDENT) {
            throw error("POINT_TARGET_NOT_STUDENT", HttpStatus.BAD_REQUEST, "Target user is not a student");
        }
    }

    private StudentPointOperationException error(String code, HttpStatus status, String message) {
        return new StudentPointOperationException(code, status, message);
    }
}
