package com.example.words.model;

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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "student_point_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_point_events_idempotency",
                columnNames = "idempotency_key"
        ),
        indexes = {
                @Index(name = "idx_student_point_events_status_retry", columnList = "status, next_retry_at"),
                @Index(name = "idx_student_point_events_student_created", columnList = "student_id, created_at"),
                @Index(name = "idx_student_point_events_source", columnList = "source_type, source_id"),
                @Index(name = "idx_student_point_events_source_key", columnList = "source_key")
        }
)
@Check(constraints = "points <> 0 AND auto_attempt_count >= 0")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudentPointEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 64)
    private PointSourceType sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_key", length = 200)
    private String sourceKey;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(name = "rule_name", length = 100)
    private String ruleName;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PointEventStatus status = PointEventStatus.PENDING;

    @Column(name = "auto_attempt_count", nullable = false)
    private Integer autoAttemptCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_trigger_type", length = 32)
    private PointAttemptTriggerType processingTriggerType;

    @Column(name = "processing_operator_id")
    private Long processingOperatorId;

    @Column(name = "processing_operator_role", length = 32)
    private String processingOperatorRole;

    @Column(name = "processing_reason", length = 500)
    private String processingReason;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_role", length = 32)
    private String operatorRole;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "transaction_id")
    private Long transactionId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
