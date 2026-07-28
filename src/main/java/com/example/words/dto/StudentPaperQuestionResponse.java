package com.example.words.dto;

import java.math.BigDecimal;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.QuestionType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentPaperQuestionResponse {

    private Long id;
    private Integer questionOrder;
    private QuestionType questionType;
    private String stem;
    private Map<String, String> options;
    private BigDecimal score;
}
