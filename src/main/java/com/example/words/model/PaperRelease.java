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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "paper_releases",
        indexes = {
                @Index(name = "idx_paper_releases_template", columnList = "paper_template_id"),
                @Index(name = "idx_paper_releases_published_by", columnList = "published_by_user_id"),
                @Index(name = "idx_paper_releases_status", columnList = "status"),
                @Index(name = "idx_paper_releases_supersedes", columnList = "supersedes_release_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PaperRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_template_id", nullable = false)
    private Long paperTemplateId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "published_by_user_id", nullable = false)
    private Long publishedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaperReleaseStatus status = PaperReleaseStatus.SCHEDULED;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount = 0;

    @Column(name = "total_score", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalScore = BigDecimal.ZERO;

    @Column(name = "shuffle_questions", nullable = false)
    private Boolean shuffleQuestions = false;

    @Column(name = "shuffle_options", nullable = false)
    private Boolean shuffleOptions = false;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "blank_answer_policy", nullable = false, length = 32)
    private PaperBlankAnswerPolicy blankAnswerPolicy = PaperBlankAnswerPolicy.ALLOW_BLANK;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_visibility", nullable = false, length = 32)
    private PaperResultVisibility resultVisibility = PaperResultVisibility.SCORE_ONLY;

    @Column(name = "results_released_at")
    private LocalDateTime resultsReleasedAt;

    @Column(name = "results_released_by_user_id")
    private Long resultsReleasedByUserId;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "withdrawn_by_user_id")
    private Long withdrawnByUserId;

    @Column(name = "withdraw_reason", length = 500)
    private String withdrawReason;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    @Column(name = "invalidated_by_user_id")
    private Long invalidatedByUserId;

    @Column(name = "invalidate_reason", length = 500)
    private String invalidateReason;

    @Column(name = "supersedes_release_id")
    private Long supersedesReleaseId;

    @Column(name = "superseded_by_release_id")
    private Long supersededByReleaseId;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @Column(name = "superseded_by_user_id")
    private Long supersededByUserId;

    @Column(name = "supersede_reason", length = 500)
    private String supersedeReason;

    @Column(name = "show_superseded_to_students", nullable = false)
    private Boolean showSupersededToStudents = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
