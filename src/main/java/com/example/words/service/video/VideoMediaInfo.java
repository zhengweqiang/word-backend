package com.example.words.service.video;

public record VideoMediaInfo(
        String mediaId,
        String mediaUrl,
        String coverUrl,
        Long durationSeconds,
        boolean ready,
        boolean preferredPlaybackReady,
        boolean cloudPublished,
        boolean cloudMediaMissing,
        String unavailableReason) {

    public VideoMediaInfo(
            String mediaId,
            String mediaUrl,
            String coverUrl,
            Long durationSeconds,
            boolean ready,
            boolean preferredPlaybackReady) {
        this(mediaId, mediaUrl, coverUrl, durationSeconds, ready, preferredPlaybackReady, false, false, null);
    }

    public VideoMediaInfo(
            String mediaId,
            String mediaUrl,
            String coverUrl,
            Long durationSeconds,
            boolean ready,
            boolean preferredPlaybackReady,
            boolean cloudPublished,
            String unavailableReason) {
        this(
                mediaId,
                mediaUrl,
                coverUrl,
                durationSeconds,
                ready,
                preferredPlaybackReady,
                cloudPublished,
                false,
                unavailableReason
        );
    }
}
