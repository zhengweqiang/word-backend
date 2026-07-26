package com.example.words.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record StudentPointRedemptionRequestDto(
        @NotBlank @Size(max = 64) String requestKey,
        @NotNull @Positive BigDecimal points,
        @NotBlank @Size(max = 500) String reason
) {
}
