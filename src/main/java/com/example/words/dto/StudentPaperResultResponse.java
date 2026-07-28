package com.example.words.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.StudentPaperAttemptStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentPaperResultResponse {

    private Long attemptId;
    private Long releaseId;
    private StudentPaperAttemptStatus status;
    private LocalDateTime submittedAt;
    private Boolean scoreVisible;
    private Boolean answersVisible;
    private BigDecimal earnedScore;
    private BigDecimal totalScore;
    private BigDecimal scorePercentage;
    private Integer answeredCount;
    private Integer correctCount;
    private List<StudentPaperResultQuestionResponse> questions;
}
