package com.example.words.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.PaperBlankAnswerPolicy;
import com.example.words.model.PaperResultVisibility;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupersedePaperReleaseRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;

    private LocalDateTime startTime;

    private LocalDateTime deadline;

    private PaperBlankAnswerPolicy blankAnswerPolicy;

    private PaperResultVisibility resultVisibility;

    private Boolean showOriginalToStudents = false;
}
