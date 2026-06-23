package com.example.words.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentVideoResponse {

    private Long id;
    private String title;
    private String description;
    private String coverUrl;
    private Long durationSeconds;
    private String createdByDisplayName;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
}
