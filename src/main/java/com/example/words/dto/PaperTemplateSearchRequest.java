package com.example.words.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.Data;

import com.example.words.model.PaperTemplateStatus;

@Data
public class PaperTemplateSearchRequest {

    private String keyword;
    private PaperTemplateStatus status;
    private Long ownerUserId;

    @Min(0)
    private Integer page = 0;

    @Min(1)
    @Max(100)
    private Integer size = 20;
}
