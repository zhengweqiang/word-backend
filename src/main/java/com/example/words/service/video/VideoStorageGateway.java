package com.example.words.service.video;

import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageProviderType;
import java.nio.file.Path;

public interface VideoStorageGateway {

    VideoStorageProviderType providerType();

    VideoUploadResult upload(
            VideoStorageConfig config,
            Path filePath,
            String originalFileName,
            String title,
            String description);

    VideoMediaInfo describeMedia(VideoStorageConfig config, String cloudMediaId);

    void deleteMedia(VideoStorageConfig config, String cloudMediaId);

    void validate(VideoStorageConfig config);
}
