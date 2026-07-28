package com.example.words.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.PaperBlankAnswerPolicy;
import com.example.words.model.PaperResultVisibility;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublishPaperRequest {

    @NotNull
    @Positive
    private Long paperId;

    private List<@NotNull @Positive Long> studentIds = new ArrayList<>();

    private List<@NotNull @Positive Long> classroomIds = new ArrayList<>();

    private LocalDateTime startTime;

    private LocalDateTime deadline;

    private PaperBlankAnswerPolicy blankAnswerPolicy;

    private PaperResultVisibility resultVisibility;

    @JsonIgnore
    @AssertTrue(message = "At least one student or classroom target is required")
    public boolean isTargetSelectionPresent() {
        return (studentIds != null && !studentIds.isEmpty())
                || (classroomIds != null && !classroomIds.isEmpty());
    }
}
