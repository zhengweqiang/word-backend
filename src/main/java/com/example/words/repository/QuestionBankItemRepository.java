package com.example.words.repository;

import java.util.List;

import com.example.words.model.QuestionBankItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionBankItemRepository
        extends JpaRepository<QuestionBankItem, Long>, JpaSpecificationExecutor<QuestionBankItem> {

    @Query("""
            SELECT DISTINCT question.category
            FROM QuestionBankItem question
            WHERE question.category IS NOT NULL
                AND question.category <> ''
                AND (:admin = true
                    OR question.createdByUserId = :actorId
                    OR question.importedByUserId = :actorId)
            ORDER BY question.category ASC
            """)
    List<String> findDistinctCategoriesVisibleTo(@Param("actorId") Long actorId, @Param("admin") boolean admin);
}
