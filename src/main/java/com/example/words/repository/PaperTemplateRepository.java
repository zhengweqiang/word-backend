package com.example.words.repository;

import com.example.words.model.PaperTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaperTemplateRepository
        extends JpaRepository<PaperTemplate, Long>, JpaSpecificationExecutor<PaperTemplate> {
}
