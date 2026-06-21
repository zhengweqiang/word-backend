package com.example.words.repository;

import com.example.words.model.MetaWord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface MetaWordRepository extends JpaRepository<MetaWord, Long> {

    Optional<MetaWord> findByWord(String word);

    Optional<MetaWord> findByNormalizedWord(String normalizedWord);

    boolean existsByWord(String word);

    boolean existsByNormalizedWord(String normalizedWord);

    List<MetaWord> findByNormalizedWordIn(Collection<String> normalizedWords);

    List<MetaWord> findByDifficulty(Integer difficulty);

    List<MetaWord> findByWordStartingWith(String prefix);

    @Query(value = """
            SELECT DISTINCT m.*
            FROM meta_words m
            JOIN dictionary_words dw ON dw.meta_word_id = m.id
            JOIN study_plans sp ON sp.dictionary_id = dw.dictionary_id
            WHERE sp.status = 'PUBLISHED'
              AND m.syllable_detail IS NULL
            ORDER BY m.id
            LIMIT :limit
            """, nativeQuery = true)
    List<MetaWord> findPublishedPlanWordsMissingSyllables(@Param("limit") int limit);

    @Query(value = "SELECT m.* FROM meta_words m WHERE m.id IN (SELECT dw.meta_word_id FROM dictionary_words dw WHERE dw.dictionary_id = :dictionaryId) LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<MetaWord> findByDictionaryIdWithPagination(@Param("dictionaryId") Long dictionaryId, @Param("limit") int limit, @Param("offset") int offset);

    @Query(value = "SELECT m.* FROM meta_words m WHERE m.id IN (SELECT dw.meta_word_id FROM dictionary_words dw WHERE dw.dictionary_id = :dictionaryId)", nativeQuery = true)
    List<MetaWord> findAllByDictionaryId(@Param("dictionaryId") Long dictionaryId);

    Page<MetaWord> findByWordStartingWith(String prefix, Pageable pageable);

    @Query("""
            SELECT m
            FROM MetaWord m
            WHERE (
                LOWER(m.word) LIKE CONCAT(LOWER(:keyword), '%')
                OR LOWER(COALESCE(m.translation, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
                OR LOWER(COALESCE(m.definition, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
                OR LOWER(COALESCE(m.phonetic, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
            )
              AND NOT EXISTS (
                SELECT dw.id
                FROM DictionaryWord dw
                WHERE dw.dictionaryId = :dictionaryId
                  AND dw.metaWordId = m.id
              )
            ORDER BY
              CASE
                WHEN LOWER(m.word) = LOWER(:keyword) THEN 0
                WHEN LOWER(m.word) LIKE CONCAT(LOWER(:keyword), '%') THEN 1
                WHEN LOWER(COALESCE(m.translation, '')) LIKE CONCAT('%', LOWER(:keyword), '%') THEN 2
                ELSE 3
              END,
              LENGTH(m.word),
              LOWER(m.word)
            """)
    List<MetaWord> findSuggestionsByDictionaryIdAndKeyword(
            @Param("dictionaryId") Long dictionaryId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query(value = "SELECT m.* FROM meta_words m WHERE m.id IN (SELECT dw.meta_word_id FROM dictionary_words dw WHERE dw.dictionary_id = :dictionaryId) AND m.word LIKE CONCAT(:keyword, '%')",
           countQuery = "SELECT COUNT(*) FROM meta_words m WHERE m.id IN (SELECT dw.meta_word_id FROM dictionary_words dw WHERE dw.dictionary_id = :dictionaryId) AND m.word LIKE CONCAT(:keyword, '%')",
           nativeQuery = true)
    Page<MetaWord> findByDictionaryIdAndWordStartingWith(@Param("dictionaryId") Long dictionaryId, @Param("keyword") String keyword, Pageable pageable);

    @Query(value = "SELECT m.* FROM meta_words m WHERE m.id IN (SELECT dw.meta_word_id FROM dictionary_words dw WHERE dw.dictionary_id = :dictionaryId)",
           countQuery = "SELECT COUNT(*) FROM meta_words m WHERE m.id IN (SELECT dw.meta_word_id FROM dictionary_words dw WHERE dw.dictionary_id = :dictionaryId)",
           nativeQuery = true)
    Page<MetaWord> findByDictionaryId(@Param("dictionaryId") Long dictionaryId, Pageable pageable);
}
