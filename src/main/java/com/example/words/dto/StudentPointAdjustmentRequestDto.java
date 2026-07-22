package com.example.words.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudentPointAdjustmentRequestDto(
        @NotBlank @Size(max = 64) String requestKey,
        @NotNull Integer amount,
        @NotBlank @Size(max = 500) String reason,
        Long replacesAdjustmentRequestId
) {
}
