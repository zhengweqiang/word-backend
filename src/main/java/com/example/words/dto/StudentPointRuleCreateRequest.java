package com.example.words.dto;

import com.example.words.model.PointSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record StudentPointRuleCreateRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotNull PointSourceType sourceType,
        @NotNull BigDecimal basePoints,
        String scopeType,
        Long scopeId,
        Boolean enabled,
        @Size(max = 500) String reason
) {
    public StudentPointRuleCreateRequest(
            String code,
            String name,
            String description,
            PointSourceType sourceType,
            int basePoints,
            String scopeType,
            Long scopeId,
            Boolean enabled,
            String reason
    ) {
        this(code, name, description, sourceType, BigDecimal.valueOf(basePoints), scopeType, scopeId, enabled, reason);
    }
}
