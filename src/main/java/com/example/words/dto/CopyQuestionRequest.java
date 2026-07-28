package com.example.words.dto;

import com.example.words.model.QuestionBankItemStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CopyQuestionRequest {

    private String stem;

    private QuestionBankItemStatus status = QuestionBankItemStatus.DRAFT;
}
