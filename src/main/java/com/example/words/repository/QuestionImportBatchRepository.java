package com.example.words.repository;

import com.example.words.model.QuestionImportBatch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface QuestionImportBatchRepository extends JpaRepository<QuestionImportBatch, Long> {

    List<QuestionImportBatch> findByImportedByUserIdOrderByCreatedAtDesc(Long importedByUserId);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE QuestionImportBatch batch
            SET batch.status = com.example.words.model.QuestionImportBatchStatus.EXPIRED
            WHERE batch.id = :batchId
              AND batch.status = com.example.words.model.QuestionImportBatchStatus.PREVIEWED
            """)
    int markExpired(@Param("batchId") Long batchId);
}
