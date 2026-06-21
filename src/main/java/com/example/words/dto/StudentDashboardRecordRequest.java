package com.example.words.dto;

import com.example.words.model.AttentionState;
import com.example.words.model.StudyActionType;
import com.example.words.model.StudyRecordResult;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentDashboardRecordRequest {

    @NotNull(message = "studentStudyPlanId is required")
    private Long studentStudyPlanId;

    @NotNull(message = "metaWordId is required")
    private Long metaWordId;

    @NotNull(message = "actionType is required")
    private StudyActionType actionType;

    @NotNull(message = "result is required")
    private StudyRecordResult result;

    @Min(0)
    private Integer durationSeconds;

    @Min(0)
    private Integer focusSeconds;

    @Min(0)
    private Integer idleSeconds;

    @Min(0)
    private Integer interactionCount;

    private AttentionState attentionState;

    public RecordStudyRequest toRecordStudyRequest() {
        return new RecordStudyRequest(
                metaWordId,
                actionType,
                result,
                durationSeconds,
                focusSeconds,
                idleSeconds,
                interactionCount,
                attentionState
        );
    }
}
