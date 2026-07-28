package com.example.words.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.QuestionImportPreviewRowStatus;
import com.example.words.model.QuestionType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionImportPreviewRowResponse {

    private Long id;
    private Integer rowNumber;
    private QuestionImportPreviewRowStatus status;
    private QuestionType questionType;
    private String stem;
    private Map<String, String> options;
    private List<String> acceptedAnswers;
    private BigDecimal score;
    private Integer difficulty;
    private List<String> tags;
    private String explanation;
    private String dictionaryName;
    private String word;
    private Long dictionaryId;
    private Long metaWordId;
    private String message;
    private Long duplicateQuestionId;
    private Map<String, String> rawRow;
}
