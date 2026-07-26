package com.example.words.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record StudentPointAdjustmentRequestDto(
        @NotBlank @Size(max = 64) String requestKey,
        @NotNull BigDecimal amount,
        @NotBlank @Size(max = 500) String reason,
        Long replacesAdjustmentRequestId
) {
    public StudentPointAdjustmentRequestDto(
            String requestKey,
            int amount,
            String reason,
            Long replacesAdjustmentRequestId
    ) {
        this(requestKey, BigDecimal.valueOf(amount), reason, replacesAdjustmentRequestId);
    }
}
