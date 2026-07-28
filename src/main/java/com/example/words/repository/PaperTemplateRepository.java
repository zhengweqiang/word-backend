package com.example.words.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import com.example.words.model.PaperTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaperTemplateRepository
        extends JpaRepository<PaperTemplate, Long>, JpaSpecificationExecutor<PaperTemplate> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT paper FROM PaperTemplate paper WHERE paper.id = :paperId")
    Optional<PaperTemplate> findByIdForUpdate(@Param("paperId") Long paperId);
}
