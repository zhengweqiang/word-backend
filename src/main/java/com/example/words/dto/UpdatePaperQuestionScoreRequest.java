package com.example.words.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePaperQuestionScoreRequest {

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 17, fraction = 2)
    private BigDecimal score;
}
