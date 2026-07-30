package com.example.words.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.QuestionType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionBankItemResponse {

    private Long id;
    private QuestionType questionType;
    private String category;
    private String stem;
    private Map<String, String> options;
    private List<String> acceptedAnswers;
    private BigDecimal defaultScore;
    private Integer difficulty;
    private List<String> tags;
    private String explanation;
    private Long dictionaryId;
    private Long metaWordId;
    private Long sourceQuestionId;
    private Long importBatchId;
    private Long createdByUserId;
    private Long importedByUserId;
    private Long lastModifiedByUserId;
    private QuestionBankItemStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime archivedAt;
}
