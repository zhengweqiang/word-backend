package com.example.words.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "question_import_preview_rows",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_question_import_preview_rows_batch_row",
                columnNames = {"batch_id", "row_number"}
        ),
        indexes = {
                @Index(name = "idx_question_import_preview_rows_batch", columnList = "batch_id"),
                @Index(name = "idx_question_import_preview_rows_status", columnList = "status")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class QuestionImportPreviewRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private QuestionImportPreviewRowStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", length = 32)
    private QuestionType questionType;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "stem", columnDefinition = "TEXT")
    private String stem;

    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "accepted_answers_json", columnDefinition = "TEXT")
    private String acceptedAnswersJson;

    @Column(name = "score", precision = 19, scale = 2)
    private BigDecimal score;

    @Column(name = "difficulty")
    private Integer difficulty;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "dictionary_name")
    private String dictionaryName;

    @Column(name = "word")
    private String word;

    @Column(name = "dictionary_id")
    private Long dictionaryId;

    @Column(name = "meta_word_id")
    private Long metaWordId;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "duplicate_question_id")
    private Long duplicateQuestionId;

    @Column(name = "raw_row_json", columnDefinition = "TEXT")
    private String rawRowJson;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
