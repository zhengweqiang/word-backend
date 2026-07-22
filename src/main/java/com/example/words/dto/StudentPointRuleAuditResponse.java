package com.example.words.dto;

import com.example.words.model.StudentPointRuleAudit;
import java.time.LocalDateTime;

public record StudentPointRuleAuditResponse(
        Long id,
        Long ruleId,
        String ruleCode,
        String action,
        Long operatorId,
        String operatorRole,
        String reason,
        String beforeSnapshot,
        String afterSnapshot,
        LocalDateTime createdAt
) {
    public static StudentPointRuleAuditResponse from(StudentPointRuleAudit audit) {
        return new StudentPointRuleAuditResponse(
                audit.getId(), audit.getRuleId(), audit.getRuleCode(), audit.getAction(), audit.getOperatorId(),
                audit.getOperatorRole(), audit.getReason(), audit.getBeforeSnapshot(), audit.getAfterSnapshot(),
                audit.getCreatedAt()
        );
    }
}
