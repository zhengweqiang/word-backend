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
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "student_point_adjustment_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_point_adjustments_request_key",
                columnNames = "request_key"
        ),
        indexes = {
                @Index(name = "idx_student_point_adjustments_student_created", columnList = "student_id, created_at"),
                @Index(name = "idx_student_point_adjustments_status_created", columnList = "status, created_at")
        }
)
@Check(constraints = "amount <> 0")
@Data
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudentPointAdjustmentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @Column(name = "request_key", nullable = false, updatable = false, length = 64)
    private String requestKey;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_role", nullable = false, length = 32)
    private String requestedRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PointAdjustmentStatus status = PointAdjustmentStatus.PENDING;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "reverse_transaction_id")
    private Long reverseTransactionId;

    @Column(name = "replaces_request_id")
    private Long replacesRequestId;

    @Column(name = "replaced_by_request_id")
    private Long replacedByRequestId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    public static StudentPointAdjustmentRequest create(
            String requestKey,
            Long studentId,
            Integer amount,
            String reason,
            Long requestedBy,
            String requestedRole,
            Long replacesRequestId
    ) {
        StudentPointAdjustmentRequest request = new StudentPointAdjustmentRequest();
        request.requestKey = requestKey;
        request.studentId = studentId;
        request.amount = amount;
        request.reason = reason;
        request.requestedBy = requestedBy;
        request.requestedRole = requestedRole;
        request.replacesRequestId = replacesRequestId;
        request.status = PointAdjustmentStatus.PENDING;
        return request;
    }
}
