package com.example.words.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.StudentPaperAttemptStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitStudentPaperResponse {

    private Long attemptId;
    private StudentPaperAttemptStatus status;
    private Long version;
    private LocalDateTime submittedAt;
    private Boolean idempotent;
    private StudentPaperResultResponse result;
}
