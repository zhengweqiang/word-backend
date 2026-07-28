package com.example.words.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentPaperAnswerResponse {

    private Long releaseQuestionId;
    private List<String> selectedAnswers;
    private List<String> blankAnswers;
}
