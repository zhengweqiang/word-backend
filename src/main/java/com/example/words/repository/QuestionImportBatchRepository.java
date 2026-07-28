package com.example.words.repository;

import com.example.words.model.QuestionImportBatch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionImportBatchRepository extends JpaRepository<QuestionImportBatch, Long> {

    List<QuestionImportBatch> findByImportedByUserIdOrderByCreatedAtDesc(Long importedByUserId);
}
