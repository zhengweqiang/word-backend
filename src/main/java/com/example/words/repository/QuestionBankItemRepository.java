package com.example.words.repository;

import com.example.words.model.QuestionBankItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface QuestionBankItemRepository
        extends JpaRepository<QuestionBankItem, Long>, JpaSpecificationExecutor<QuestionBankItem> {
}
