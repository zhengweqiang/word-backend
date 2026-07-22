package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(
        name = "student_point_event_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_point_event_attempts_event_no",
                columnNames = {"event_id", "attempt_no"}
        ),
        indexes = {
                @Index(name = "idx_student_point_event_attempts_event_started", columnList = "event_id, started_at"),
                @Index(
                        name = "idx_student_point_event_attempts_trigger_started",
                        columnList = "trigger_type, started_at"
                )
        }
)
@Check(constraints = "attempt_no > 0")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentPointEventAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private PointAttemptTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PointEventAttemptStatus status;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_role", length = 32)
    private String operatorRole;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
