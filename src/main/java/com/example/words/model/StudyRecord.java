package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "study_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_study_records_request_key",
                columnNames = "request_key"
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class StudyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @Column(name = "request_key", nullable = false, updatable = false, length = 64)
    private String requestKey;

    @Column(name = "student_study_plan_id", nullable = false)
    private Long studentStudyPlanId;

    @Column(name = "meta_word_id", nullable = false)
    private Long metaWordId;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private StudyActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private StudyRecordResult result;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "focus_seconds")
    private Integer focusSeconds;

    @Column(name = "idle_seconds")
    private Integer idleSeconds;

    @Column(name = "interaction_count", nullable = false)
    @Builder.Default
    private Integer interactionCount = 0;

    @Column(name = "points_eligible", nullable = false)
    @Builder.Default
    private boolean pointsEligible = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "attention_state")
    private AttentionState attentionState;

    @Column(name = "stage_before")
    private Integer stageBefore;

    @Column(name = "stage_after")
    private Integer stageAfter;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
