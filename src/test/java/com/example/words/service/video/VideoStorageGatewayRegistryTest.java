package com.example.words.service.video;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.words.exception.BadRequestException;
import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageProviderType;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class VideoStorageGatewayRegistryTest {

    @Test
    void getShouldReturnGatewayForProviderType() {
        VideoStorageGateway tencentGateway = new FakeGateway(VideoStorageProviderType.TENCENT_VOD);
        VideoStorageGateway volcengineGateway = new FakeGateway(VideoStorageProviderType.VOLCENGINE_VOD);
        VideoStorageGatewayRegistry registry = new VideoStorageGatewayRegistry(List.of(tencentGateway, volcengineGateway));

        assertSame(tencentGateway, registry.get(VideoStorageProviderType.TENCENT_VOD));
        assertSame(volcengineGateway, registry.get(VideoStorageProviderType.VOLCENGINE_VOD));
    }

    @Test
    void getShouldRejectMissingProviderType() {
        VideoStorageGatewayRegistry registry = new VideoStorageGatewayRegistry(List.of());

        assertThrows(BadRequestException.class, () -> registry.get(VideoStorageProviderType.TENCENT_VOD));
    }

    private record FakeGateway(VideoStorageProviderType providerType) implements VideoStorageGateway {

        @Override
        public VideoUploadResult upload(
                VideoStorageConfig config,
                Path filePath,
                String originalFileName,
                String title,
                String description) {
            return null;
        }

        @Override
        public VideoMediaInfo describeMedia(VideoStorageConfig config, String cloudMediaId) {
            return null;
        }

        @Override
        public void deleteMedia(VideoStorageConfig config, String cloudMediaId) {
        }

        @Override
        public void validate(VideoStorageConfig config) {
        }
    }
}
