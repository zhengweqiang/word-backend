package com.example.words.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "paper_release_targets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_paper_release_targets_student",
                columnNames = {"paper_release_id", "student_id"}
        ),
        indexes = {
                @Index(name = "idx_paper_release_targets_release", columnList = "paper_release_id"),
                @Index(name = "idx_paper_release_targets_student", columnList = "student_id"),
                @Index(name = "idx_paper_release_targets_classroom", columnList = "source_classroom_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PaperReleaseTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_release_id", nullable = false)
    private Long paperReleaseId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "source_classroom_id")
    private Long sourceClassroomId;

    @Column(name = "source_classroom_ids_json", nullable = false, columnDefinition = "TEXT")
    private String sourceClassroomIdsJson = "[]";

    @Column(name = "targeted_by_user_id", nullable = false)
    private Long targetedByUserId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
