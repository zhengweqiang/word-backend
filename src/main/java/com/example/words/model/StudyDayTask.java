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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "study_day_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudyDayTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_study_plan_id", nullable = false)
    private Long studentStudyPlanId;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Column(name = "new_count", nullable = false)
    private Integer newCount = 0;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount = 0;

    @Column(name = "overdue_count", nullable = false)
    private Integer overdueCount = 0;

    @Column(name = "completed_count", nullable = false)
    private Integer completedCount = 0;

    @Column(name = "completion_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionRate = BigDecimal.ZERO;

    @Column(name = "total_focus_seconds", nullable = false)
    private Integer totalFocusSeconds = 0;

    @Column(name = "avg_focus_seconds_per_word", nullable = false, precision = 10, scale = 2)
    private BigDecimal avgFocusSecondsPerWord = BigDecimal.ZERO;

    @Column(name = "max_focus_seconds_per_word", nullable = false)
    private Integer maxFocusSecondsPerWord = 0;

    @Column(name = "attention_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal attentionScore = BigDecimal.ZERO;

    @Column(name = "idle_interrupt_count", nullable = false)
    private Integer idleInterruptCount = 0;

    @Column(name = "points_eligible", nullable = false)
    private boolean pointsEligible = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudyDayTaskStatus status = StudyDayTaskStatus.NOT_STARTED;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
