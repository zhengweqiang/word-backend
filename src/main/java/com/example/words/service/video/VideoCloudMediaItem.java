package com.example.words.service.video;

public record VideoCloudMediaItem(
        String mediaId,
        String title,
        String description,
        String originalFileName,
        String contentType,
        Long fileSize,
        String mediaUrl,
        String coverUrl,
        Long durationSeconds,
        boolean ready,
        boolean cloudPublished) {
}
