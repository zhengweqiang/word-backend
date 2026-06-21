package com.example.words.dto;

import com.example.words.model.StudentStudyPlanStatus;
import com.example.words.model.StudyDayTaskStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentStudyPlanSummaryResponse {

    private Long studentStudyPlanId;
    private Long studyPlanId;
    private String planName;
    private LocalDateTime planPublishedAt;
    private Long dictionaryId;
    private String dictionaryName;
    private StudentStudyPlanStatus status;
    private BigDecimal overallProgress;
    private Integer currentStreak;
    private LocalDateTime lastStudyAt;
    private LocalDate taskDate;
    private StudyDayTaskStatus todayStatus;
    private Integer totalTaskCount;
    private Integer completedCount;
    private BigDecimal completionRate;
    private BigDecimal avgFocusSeconds;
    private BigDecimal attentionScore;
}
