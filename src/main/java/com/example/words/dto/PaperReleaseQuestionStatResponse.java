package com.example.words.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.words.model.QuestionType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperReleaseQuestionStatResponse {

    private Long releaseQuestionId;
    private Integer questionOrder;
    private QuestionType questionType;
    private String category;
    private String stem;
    private int submissionCount;
    private int answeredCount;
    private int correctCount;
    private BigDecimal correctnessRate;
}
