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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "question_bank_items",
        indexes = {
                @Index(name = "idx_question_bank_items_type", columnList = "question_type"),
                @Index(name = "idx_question_bank_items_status", columnList = "status"),
                @Index(name = "idx_question_bank_items_created_by", columnList = "created_by_user_id"),
                @Index(name = "idx_question_bank_items_source_question", columnList = "source_question_id"),
                @Index(name = "idx_question_bank_items_import_batch", columnList = "import_batch_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class QuestionBankItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 32)
    private QuestionType questionType;

    @Column(name = "stem", nullable = false, columnDefinition = "TEXT")
    private String stem;

    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "accepted_answers_json", nullable = false, columnDefinition = "TEXT")
    private String acceptedAnswersJson;

    @Column(name = "default_score", nullable = false, precision = 19, scale = 2)
    private BigDecimal defaultScore;

    @Column(name = "difficulty")
    private Integer difficulty;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "dictionary_id")
    private Long dictionaryId;

    @Column(name = "meta_word_id")
    private Long metaWordId;

    @Column(name = "source_question_id")
    private Long sourceQuestionId;

    @Column(name = "import_batch_id")
    private Long importBatchId;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "imported_by_user_id")
    private Long importedByUserId;

    @Column(name = "last_modified_by_user_id")
    private Long lastModifiedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private QuestionBankItemStatus status = QuestionBankItemStatus.DRAFT;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
}
