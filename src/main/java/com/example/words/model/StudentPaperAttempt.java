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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "student_paper_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_paper_attempts_release_student",
                columnNames = {"paper_release_id", "student_id"}
        ),
        indexes = {
                @Index(name = "idx_student_paper_attempts_release", columnList = "paper_release_id"),
                @Index(name = "idx_student_paper_attempts_student", columnList = "student_id"),
                @Index(name = "idx_student_paper_attempts_status", columnList = "status")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudentPaperAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_release_id", nullable = false)
    private Long paperReleaseId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private StudentPaperAttemptStatus status = StudentPaperAttemptStatus.NOT_STARTED;

    @Column(name = "answered_count", nullable = false)
    private Integer answeredCount = 0;

    @Column(name = "correct_count", nullable = false)
    private Integer correctCount = 0;

    @Column(name = "earned_score", nullable = false, precision = 19, scale = 2)
    private BigDecimal earnedScore = BigDecimal.ZERO;

    @Column(name = "total_score", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalScore = BigDecimal.ZERO;

    @Column(name = "score_percentage", precision = 5, scale = 2)
    private BigDecimal scorePercentage;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "last_draft_saved_at")
    private LocalDateTime lastDraftSavedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    @Column(name = "invalidated_by_user_id")
    private Long invalidatedByUserId;

    @Column(name = "invalidate_reason", length = 500)
    private String invalidateReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
