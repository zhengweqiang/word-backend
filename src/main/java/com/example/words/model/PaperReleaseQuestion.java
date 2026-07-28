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
        name = "paper_release_questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_paper_release_questions_order",
                columnNames = {"paper_release_id", "question_order"}
        ),
        indexes = {
                @Index(name = "idx_paper_release_questions_release", columnList = "paper_release_id"),
                @Index(name = "idx_paper_release_questions_source", columnList = "source_question_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PaperReleaseQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_release_id", nullable = false)
    private Long paperReleaseId;

    @Column(name = "paper_template_question_id")
    private Long paperTemplateQuestionId;

    @Column(name = "source_question_id")
    private Long sourceQuestionId;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 32)
    private QuestionType questionType;

    @Column(name = "stem", nullable = false, columnDefinition = "TEXT")
    private String stem;

    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "accepted_answers_json", nullable = false, columnDefinition = "TEXT")
    private String acceptedAnswersJson;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "score", nullable = false, precision = 19, scale = 2)
    private BigDecimal score;

    @Column(name = "dictionary_id")
    private Long dictionaryId;

    @Column(name = "meta_word_id")
    private Long metaWordId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
