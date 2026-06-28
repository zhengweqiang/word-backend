package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "classroom_group_feed_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomGroupFeedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    private ClassroomGroupFeedMessageType messageType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "resource_title")
    private String resourceTitle;

    @Column(name = "resource_summary", columnDefinition = "TEXT")
    private String resourceSummary;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
