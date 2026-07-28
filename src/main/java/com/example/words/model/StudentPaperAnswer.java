package com.example.words.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "student_paper_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_paper_answers_question",
                columnNames = {"attempt_id", "release_question_id"}
        ),
        indexes = {
                @Index(name = "idx_student_paper_answers_attempt", columnList = "attempt_id"),
                @Index(name = "idx_student_paper_answers_release", columnList = "paper_release_id"),
                @Index(name = "idx_student_paper_answers_question", columnList = "release_question_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudentPaperAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "paper_release_id", nullable = false)
    private Long paperReleaseId;

    @Column(name = "release_question_id", nullable = false)
    private Long releaseQuestionId;

    @Column(name = "selected_answers_json", columnDefinition = "TEXT")
    private String selectedAnswersJson;

    @Column(name = "blank_answers_json", columnDefinition = "TEXT")
    private String blankAnswersJson;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "earned_score", precision = 19, scale = 2)
    private BigDecimal earnedScore;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
