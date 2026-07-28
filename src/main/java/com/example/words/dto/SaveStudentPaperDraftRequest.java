package com.example.words.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveStudentPaperDraftRequest {

    @NotNull
    private Long expectedVersion;

    @NotNull
    @Valid
    private List<StudentPaperAnswerRequest> answers = new ArrayList<>();
}
