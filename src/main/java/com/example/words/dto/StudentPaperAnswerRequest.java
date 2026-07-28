package com.example.words.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentPaperAnswerRequest {

    @NotNull
    private Long releaseQuestionId;

    private List<String> selectedAnswers = new ArrayList<>();

    private List<String> blankAnswers = new ArrayList<>();
}
