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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "paper_templates",
        indexes = {
                @Index(name = "idx_paper_templates_owner", columnList = "owner_user_id"),
                @Index(name = "idx_paper_templates_status", columnList = "status"),
                @Index(name = "idx_paper_templates_source", columnList = "source_paper_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PaperTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "source_paper_id")
    private Long sourcePaperId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaperTemplateStatus status = PaperTemplateStatus.DRAFT;

    @Column(name = "shuffle_questions", nullable = false)
    private Boolean shuffleQuestions = false;

    @Column(name = "shuffle_options", nullable = false)
    private Boolean shuffleOptions = false;

    @Column(name = "total_score", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalScore = BigDecimal.ZERO;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
}
