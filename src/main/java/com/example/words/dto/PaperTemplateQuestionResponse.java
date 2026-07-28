package com.example.words.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.QuestionType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperTemplateQuestionResponse {

    private Long id;
    private Long sourceQuestionId;
    private Integer questionOrder;
    private QuestionType questionType;
    private String stem;
    private Map<String, String> options;
    private List<String> acceptedAnswers;
    private String explanation;
    private BigDecimal score;
    private Long dictionaryId;
    private Long metaWordId;
    private LocalDateTime createdAt;
}
