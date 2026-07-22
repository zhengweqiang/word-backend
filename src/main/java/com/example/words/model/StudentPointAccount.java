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
        name = "student_point_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_student_point_accounts_student", columnNames = "student_id")
)
@Check(constraints = "available_points >= 0 AND frozen_points >= 0 "
        + "AND lifetime_earned_points >= 0 AND lifetime_spent_points >= 0")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudentPointAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "available_points", nullable = false)
    private Integer availablePoints = 0;

    @Column(name = "frozen_points", nullable = false)
    private Integer frozenPoints = 0;

    @Column(name = "lifetime_earned_points", nullable = false)
    private Integer lifetimeEarnedPoints = 0;

    @Column(name = "lifetime_spent_points", nullable = false)
    private Integer lifetimeSpentPoints = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PointAccountStatus status = PointAccountStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static StudentPointAccount create(Long studentId) {
        StudentPointAccount account = new StudentPointAccount();
        account.setStudentId(studentId);
        return account;
    }
}
