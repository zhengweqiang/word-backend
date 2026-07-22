package com.example.words.dto;

import com.example.words.model.PointSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudentPointRuleUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotNull PointSourceType sourceType,
        @NotNull Integer basePoints,
        String scopeType,
        Long scopeId,
        @NotNull Boolean enabled,
        @NotBlank @Size(max = 500) String reason
) {
}
