package com.example.words.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.StudentPaperAttemptStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperReleaseTargetResponse {

    private Long id;
    private Long studentId;
    private List<Long> sourceClassroomIds;
    private Long attemptId;
    private StudentPaperAttemptStatus attemptStatus;
}
