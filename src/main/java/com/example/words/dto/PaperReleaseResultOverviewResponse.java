package com.example.words.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.PaperResultVisibility;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperReleaseResultOverviewResponse {

    private Long releaseId;
    private String title;
    private PaperReleaseStatus releaseStatus;
    private int assignedCount;
    private int notStartedCount;
    private int inProgressCount;
    private int overdueCount;
    private int submittedCount;
    private int submittedLateCount;
    private int completedCount;
    private PaperResultVisibility resultVisibility;
    private Boolean resultsReleased;
    private LocalDateTime resultsReleasedAt;
    private Long resultsReleasedByUserId;
    private List<PaperReleaseStudentResultResponse> students;
}
