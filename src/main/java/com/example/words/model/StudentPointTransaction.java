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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "student_point_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_point_transactions_idempotency",
                columnNames = "idempotency_key"
        ),
        indexes = {
                @Index(name = "idx_student_point_transactions_student_created", columnList = "student_id, created_at"),
                @Index(name = "idx_student_point_transactions_source", columnList = "source_type, source_id"),
                @Index(name = "idx_student_point_transactions_source_key", columnList = "source_key"),
                @Index(name = "idx_student_point_transactions_operator_created", columnList = "operator_id, created_at")
        }
)
@Check(constraints = "amount <> 0")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudentPointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32)
    private PointTransactionType transactionType;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "balance_before", nullable = false)
    private Integer balanceBefore;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "frozen_before", nullable = false)
    private Integer frozenBefore = 0;

    @Column(name = "frozen_after", nullable = false)
    private Integer frozenAfter = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 64)
    private PointSourceType sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_key", length = 200)
    private String sourceKey;

    @Column(name = "rule_code", length = 64)
    private String ruleCode;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_role", length = 32)
    private String operatorRole;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "reversed_transaction_id")
    private Long reversedTransactionId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
