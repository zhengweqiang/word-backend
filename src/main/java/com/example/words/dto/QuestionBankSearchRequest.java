package com.example.words.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.Data;

import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.QuestionType;

@Data
public class QuestionBankSearchRequest {

    private String keyword;
    private QuestionType questionType;
    private String category;
    private QuestionBankItemStatus status;
    private String tag;
    private Long dictionaryId;
    private Long metaWordId;
    private Long creatorId;

    @Min(0)
    private Integer page = 0;

    @Min(1)
    @Max(100)
    private Integer size = 20;
}
