package com.example.words.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.PaperBlankAnswerPolicy;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.StudentPaperAttemptStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentPaperAttemptResponse {

    private Long attemptId;
    private Long releaseId;
    private String title;
    private String instructions;
    private PaperReleaseStatus releaseStatus;
    private StudentPaperAttemptStatus attemptStatus;
    private Long version;
    private Integer questionCount;
    private BigDecimal totalScore;
    private LocalDateTime startTime;
    private LocalDateTime deadline;
    private PaperBlankAnswerPolicy blankAnswerPolicy;
    private Boolean answerable;
    private List<StudentPaperQuestionResponse> questions;
    private List<StudentPaperAnswerResponse> answers;
}
