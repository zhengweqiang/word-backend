package com.example.words.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentAssessmentSummaryResponse {

    private StudentAssessmentType assessmentType;
    private StudentAssessmentStatus status;
    private Long assessmentId;
    private Long legacyExamId;
    private Long paperAttemptId;
    private Long paperReleaseId;
    private Long dictionaryId;
    private Long paperTemplateId;
    private String title;
    private Integer questionCount;
    private Integer answeredCount;
    private Boolean scoreVisible;
    private Integer correctCount;
    private BigDecimal earnedScore;
    private BigDecimal totalScore;
    private BigDecimal scorePercentage;
    private LocalDateTime assignedAt;
    private LocalDateTime startTime;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
}
