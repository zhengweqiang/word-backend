package com.example.words.dto;

import com.example.words.model.ResourceScopeType;
import com.example.words.model.VideoCloudPublishStatus;
import com.example.words.model.VideoStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoResponse {

    private Long id;
    private String title;
    private String description;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String tencentFileId;
    private String mediaUrl;
    private String coverUrl;
    private Long durationSeconds;
    private VideoStatus status;
    private VideoCloudPublishStatus cloudPublishStatus;
    private String errorMessage;
    private Long createdBy;
    private String createdByDisplayName;
    private Long ownerUserId;
    private ResourceScopeType scopeType;
    private Long storageConfigId;
    private String storageConfigName;
    private boolean canManage;
    private boolean canPreview;
    private LocalDateTime publishedAt;
    private LocalDateTime unpublishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
