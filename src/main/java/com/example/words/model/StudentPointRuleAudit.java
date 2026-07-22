package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "student_point_rule_audits",
        indexes = {
                @Index(name = "idx_student_point_rule_audits_rule_created", columnList = "rule_id, created_at"),
                @Index(name = "idx_student_point_rule_audits_operator_created", columnList = "operator_id, created_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentPointRuleAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "operator_role", nullable = false, length = 32)
    private String operatorRole;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "before_snapshot", columnDefinition = "TEXT")
    private String beforeSnapshot;

    @Column(name = "after_snapshot", nullable = false, columnDefinition = "TEXT")
    private String afterSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
