package com.example.words.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudentPointAdminReasonRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
