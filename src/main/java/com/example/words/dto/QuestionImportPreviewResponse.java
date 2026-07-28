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
public class QuestionImportPreviewResponse {

    private Long batchId;
    private String fileName;
    private Integer totalRows;
    private Integer validRows;
    private Integer invalidRows;
    private Integer duplicateRows;
    private QuestionImportBatchStatus status;
    private LocalDateTime expiresAt;
    private List<QuestionImportPreviewRowResponse> rows;
}
