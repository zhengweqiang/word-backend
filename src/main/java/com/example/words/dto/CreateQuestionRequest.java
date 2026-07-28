package com.example.words.dto;

import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.QuestionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuestionRequest {

    @NotNull
    private QuestionType questionType;

    @NotBlank
    private String stem;

    @Size(max = 4)
    private Map<String, String> options;

    @NotEmpty
    private List<@NotBlank String> acceptedAnswers;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal defaultScore;

    private Integer difficulty;

    private List<String> tags;

    private String explanation;

    private Long dictionaryId;

    private Long metaWordId;

    private QuestionBankItemStatus status = QuestionBankItemStatus.DRAFT;
}
