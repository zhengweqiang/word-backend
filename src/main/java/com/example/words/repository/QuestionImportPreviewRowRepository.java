package com.example.words.repository;

import com.example.words.model.QuestionImportPreviewRow;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionImportPreviewRowRepository extends JpaRepository<QuestionImportPreviewRow, Long> {

    List<QuestionImportPreviewRow> findByBatchIdOrderByRowNumberAsc(Long batchId);
}
