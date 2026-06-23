package com.example.words.service.video;

public record VideoUploadResult(
        String mediaId,
        String mediaUrl,
        String coverUrl,
        boolean transcodeRequested) {
}
