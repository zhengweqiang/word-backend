package com.example.words.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.words.model.QuestionCategory;

public interface QuestionCategoryRepository extends JpaRepository<QuestionCategory, Long> {

    @Query("""
            select count(category) > 0
            from QuestionCategory category
            where category.deletedAt is null
              and lower(category.name) = lower(:name)
            """)
    boolean existsActiveByNameIgnoreCase(String name);

    @Query("""
            select count(category) > 0
            from QuestionCategory category
            where category.deletedAt is null
              and category.id <> :id
              and lower(category.name) = lower(:name)
            """)
    boolean existsActiveByNameIgnoreCaseAndIdNot(String name, Long id);

    Optional<QuestionCategory> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            select category
            from QuestionCategory category
            where category.deletedAt is null
            order by lower(category.name) asc, category.id asc
            """)
    List<QuestionCategory> findActiveOrderByNameAsc();
}
