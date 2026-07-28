package com.example.words.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.QuestionType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentPaperResultQuestionResponse {

    private Long releaseQuestionId;
    private Integer questionOrder;
    private QuestionType questionType;
    private String stem;
    private Map<String, String> options;
    private List<String> selectedAnswers;
    private List<String> blankAnswers;
    private Boolean correct;
    private BigDecimal earnedScore;
    private BigDecimal questionScore;
    private List<String> acceptedAnswers;
    private String explanation;
}
