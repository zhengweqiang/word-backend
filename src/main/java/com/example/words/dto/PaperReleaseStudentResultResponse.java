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
public class PaperReleaseStudentResultResponse {

    private Long releaseId;
    private Long attemptId;
    private Long studentId;
    private StudentPaperAttemptStatus status;
    private Boolean late;
    private Integer answeredCount;
    private Integer correctCount;
    private BigDecimal earnedScore;
    private BigDecimal totalScore;
    private BigDecimal scorePercentage;
    private LocalDateTime submittedAt;
    private List<StudentPaperResultQuestionResponse> questions;
}
