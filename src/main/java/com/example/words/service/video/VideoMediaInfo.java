package com.example.words.service.video;

public record VideoMediaInfo(
        String mediaId,
        String mediaUrl,
        String coverUrl,
        Long durationSeconds,
        boolean ready,
        boolean preferredPlaybackReady) {
}
