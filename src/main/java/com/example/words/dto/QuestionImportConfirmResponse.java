package com.example.words.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.QuestionImportBatchStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionImportConfirmResponse {

    private Long batchId;
    private Integer importedCount;
    private List<Long> importedQuestionIds;
    private QuestionImportBatchStatus status;
    private LocalDateTime confirmedAt;
}
