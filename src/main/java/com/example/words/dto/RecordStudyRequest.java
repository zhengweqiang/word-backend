package com.example.words.dto;

import com.example.words.model.AttentionState;
import com.example.words.model.StudyActionType;
import com.example.words.model.StudyRecordResult;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecordStudyRequest {

    @NotNull(message = "metaWordId is required")
    private Long metaWordId;

    @NotNull(message = "actionType is required")
    private StudyActionType actionType;

    @NotNull(message = "result is required")
    private StudyRecordResult result;

    @Min(value = 0, message = "durationSeconds must be greater than or equal to 0")
    private Integer durationSeconds;

    @Min(value = 0, message = "focusSeconds must be greater than or equal to 0")
    private Integer focusSeconds;

    @Min(value = 0, message = "idleSeconds must be greater than or equal to 0")
    private Integer idleSeconds;

    @Min(value = 0, message = "interactionCount must be greater than or equal to 0")
    private Integer interactionCount;

    private AttentionState attentionState;

    @NotBlank(message = "requestKey is required")
    @Size(max = 64, message = "requestKey must not exceed 64 characters")
    private String requestKey;
}
