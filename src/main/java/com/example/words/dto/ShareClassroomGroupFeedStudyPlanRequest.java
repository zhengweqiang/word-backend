package com.example.words.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareClassroomGroupFeedStudyPlanRequest {

    @NotNull(message = "studyPlanId is required")
    private Long studyPlanId;
}
