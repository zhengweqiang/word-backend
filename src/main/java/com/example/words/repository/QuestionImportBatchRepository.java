package com.example.words.repository;

import com.example.words.model.QuestionImportBatch;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionImportBatchRepository extends JpaRepository<QuestionImportBatch, Long> {

    List<QuestionImportBatch> findByImportedByUserIdOrderByCreatedAtDesc(Long importedByUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT batch FROM QuestionImportBatch batch WHERE batch.id = :batchId")
    Optional<QuestionImportBatch> findByIdForUpdate(@Param("batchId") Long batchId);
}
