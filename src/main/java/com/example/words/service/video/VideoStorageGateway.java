package com.example.words.service.video;

import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageProviderType;
import java.nio.file.Path;
import java.util.List;

public interface VideoStorageGateway {

    VideoStorageProviderType providerType();

    VideoUploadResult upload(
            VideoStorageConfig config,
            Path filePath,
            String originalFileName,
            String title,
            String description);

    VideoMediaInfo describeMedia(VideoStorageConfig config, String cloudMediaId);

    default List<VideoCloudMediaItem> listMedia(VideoStorageConfig config, int offset, int pageSize) {
        throw new UnsupportedOperationException(providerType() + " does not support cloud media listing");
    }

    default void publishMedia(VideoStorageConfig config, String cloudMediaId) {
        throw new UnsupportedOperationException(providerType() + " does not support cloud media publishing");
    }

    default void unpublishMedia(VideoStorageConfig config, String cloudMediaId) {
        throw new UnsupportedOperationException(providerType() + " does not support cloud media unpublishing");
    }

    void deleteMedia(VideoStorageConfig config, String cloudMediaId);

    void validate(VideoStorageConfig config);
}
