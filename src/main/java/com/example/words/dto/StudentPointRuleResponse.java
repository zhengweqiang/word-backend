package com.example.words.dto;

import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointRule;
import java.time.LocalDateTime;

public record StudentPointRuleResponse(
        Long id,
        String code,
        String name,
        String description,
        PointSourceType sourceType,
        Integer basePoints,
        String scopeType,
        Long scopeId,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static StudentPointRuleResponse from(StudentPointRule rule) {
        return new StudentPointRuleResponse(
                rule.getId(), rule.getCode(), rule.getName(), rule.getDescription(), rule.getSourceType(),
                rule.getBasePoints(), rule.getScopeType(), rule.getScopeId(), rule.getEnabled(),
                rule.getCreatedAt(), rule.getUpdatedAt()
        );
    }
}
