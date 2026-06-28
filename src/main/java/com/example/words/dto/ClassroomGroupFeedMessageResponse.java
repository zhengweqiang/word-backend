package com.example.words.dto;

import com.example.words.model.ClassroomGroupFeedMessageType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomGroupFeedMessageResponse {

    private Long id;

    private Long classroomId;

    private ClassroomGroupFeedMessageType messageType;

    private String content;

    private Long resourceId;

    private String resourceTitle;

    private String resourceSummary;

    private Long authorUserId;

    private String authorName;

    private LocalDateTime createdAt;
}
