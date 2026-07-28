package com.example.words.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.PaperTemplateStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperTemplateResponse {

    private Long id;
    private String title;
    private String instructions;
    private Long ownerUserId;
    private Long sourcePaperId;
    private PaperTemplateStatus status;
    private Boolean shuffleQuestions;
    private Boolean shuffleOptions;
    private BigDecimal totalScore;
    private Integer questionCount;
    private List<PaperTemplateQuestionResponse> questions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime archivedAt;
}
