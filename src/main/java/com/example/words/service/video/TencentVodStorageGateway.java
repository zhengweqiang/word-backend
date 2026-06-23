package com.example.words.service.video;

import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageProviderType;
import com.example.words.service.TencentVodGateway;
import com.example.words.service.TencentVodMediaInfo;
import com.example.words.service.TencentVodUploadResult;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class TencentVodStorageGateway implements VideoStorageGateway {

    private final TencentVodGateway tencentVodGateway;

    public TencentVodStorageGateway(TencentVodGateway tencentVodGateway) {
        this.tencentVodGateway = tencentVodGateway;
    }

    @Override
    public VideoStorageProviderType providerType() {
        return VideoStorageProviderType.TENCENT_VOD;
    }

    @Override
    public VideoUploadResult upload(
            VideoStorageConfig config,
            Path filePath,
            String originalFileName,
            String title,
            String description) {
        TencentVodUploadResult result = tencentVodGateway.upload(
                config,
                filePath,
                originalFileName,
                title,
                description
        );
        return new VideoUploadResult(
                result.fileId(),
                result.mediaUrl(),
                result.coverUrl(),
                result.transcodeRequested()
        );
    }

    @Override
    public VideoMediaInfo describeMedia(VideoStorageConfig config, String cloudMediaId) {
        TencentVodMediaInfo mediaInfo = tencentVodGateway.describeMedia(config, cloudMediaId);
        return new VideoMediaInfo(
                mediaInfo.fileId(),
                mediaInfo.mediaUrl(),
                mediaInfo.coverUrl(),
                mediaInfo.durationSeconds(),
                mediaInfo.ready(),
                mediaInfo.preferredPlaybackReady()
        );
    }

    @Override
    public void deleteMedia(VideoStorageConfig config, String cloudMediaId) {
        tencentVodGateway.deleteMedia(config, cloudMediaId);
    }

    @Override
    public void validate(VideoStorageConfig config) {
        tencentVodGateway.validate(config);
    }
}
