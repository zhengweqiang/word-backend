package com.example.words.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.StudentPaperAttemptStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentAssignedPaperSummaryResponse {

    private Long attemptId;
    private Long releaseId;
    private String title;
    private String instructions;
    private PaperReleaseStatus releaseStatus;
    private StudentPaperAttemptStatus attemptStatus;
    private Integer questionCount;
    private BigDecimal totalScore;
    private LocalDateTime startTime;
    private LocalDateTime deadline;
    private Boolean answerable;
    private Boolean resultAvailable;
}
