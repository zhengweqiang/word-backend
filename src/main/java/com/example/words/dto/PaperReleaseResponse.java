package com.example.words.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.PaperBlankAnswerPolicy;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.PaperResultVisibility;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperReleaseResponse {

    private Long id;
    private Long paperTemplateId;
    private String title;
    private String instructions;
    private Long publishedByUserId;
    private PaperReleaseStatus status;
    private Integer questionCount;
    private BigDecimal totalScore;
    private List<String> categories;
    private Boolean shuffleQuestions;
    private Boolean shuffleOptions;
    private LocalDateTime startTime;
    private LocalDateTime deadline;
    private PaperBlankAnswerPolicy blankAnswerPolicy;
    private PaperResultVisibility resultVisibility;
    private LocalDateTime withdrawnAt;
    private Long withdrawnByUserId;
    private String withdrawReason;
    private LocalDateTime invalidatedAt;
    private Long invalidatedByUserId;
    private String invalidateReason;
    private Long supersedesReleaseId;
    private Long supersededByReleaseId;
    private LocalDateTime supersededAt;
    private Long supersededByUserId;
    private String supersedeReason;
    private Boolean showSupersededToStudents;
    private LocalDateTime createdAt;
    private List<PaperReleaseTargetResponse> targets;
}
