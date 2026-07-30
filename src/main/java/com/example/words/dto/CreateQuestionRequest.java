package com.example.words.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.QuestionType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuestionRequest {

    @NotNull
    private QuestionType questionType;

    @Size(max = 100)
    private String category;

    @NotBlank
    private String stem;

    @Size(max = 4)
    private Map<String, String> options;

    @NotEmpty
    private List<@NotBlank String> acceptedAnswers;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 17, fraction = 2)
    private BigDecimal defaultScore;

    private Integer difficulty;

    private List<String> tags;

    private String explanation;

    private Long dictionaryId;

    private Long metaWordId;

    private QuestionBankItemStatus status = QuestionBankItemStatus.DRAFT;

    public CreateQuestionRequest(
            QuestionType questionType,
            String stem,
            Map<String, String> options,
            List<String> acceptedAnswers,
            BigDecimal defaultScore,
            Integer difficulty,
            List<String> tags,
            String explanation,
            Long dictionaryId,
            Long metaWordId,
            QuestionBankItemStatus status) {
        this(
                questionType,
                null,
                stem,
                options,
                acceptedAnswers,
                defaultScore,
                difficulty,
                tags,
                explanation,
                dictionaryId,
                metaWordId,
                status);
    }
}
