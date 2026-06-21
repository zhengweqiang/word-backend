package com.example.words.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentDashboardResponse {

    private LocalDate taskDate;
    private Boolean hasPlans;
    private Boolean allTasksCompleted;
    private Integer overdueCount;
    private Integer reviewCount;
    private Integer newCount;
    private Integer completedCount;
    private Integer totalCount;
    private BigDecimal completionRate;
    private List<StudentDashboardReminderResponse> reminders;
    private List<StudentDashboardTaskItemResponse> queue;
}
