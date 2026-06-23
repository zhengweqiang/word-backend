package com.example.words.service.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.words.exception.BadRequestException;
import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageProviderType;
import com.example.words.service.VideoStorageConfigCryptoService;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VolcengineVodStorageGatewayTest {

    private VideoStorageConfigCryptoService cryptoService;
    private VolcengineVodStorageGateway gateway;

    @BeforeEach
    void setUp() {
        cryptoService = new VideoStorageConfigCryptoService(
                Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes())
        );
        gateway = new VolcengineVodStorageGateway(cryptoService);
    }

    @Test
    void providerTypeShouldBeVolcengineVod() {
        assertEquals(VideoStorageProviderType.VOLCENGINE_VOD, gateway.providerType());
    }

    @Test
    void validateShouldRejectMissingSpaceNameBeforeCallingVolcengine() {
        VideoStorageConfig config = readyConfig();
        config.setSpaceName(" ");

        BadRequestException exception = assertThrows(BadRequestException.class, () -> gateway.validate(config));

        assertEquals("spaceName is required for Volcengine VOD", exception.getMessage());
    }

    @Test
    void validateShouldRejectPlaceholderSecretsBeforeCallingVolcengine() {
        VideoStorageConfig config = readyConfig();
        config.setSecretIdEncrypted(cryptoService.encrypt("1"));
        config.setSecretKeyEncrypted(cryptoService.encrypt("1"));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> gateway.validate(config));

        assertEquals(
                "Current video storage config still uses placeholder AccessKeyId/SecretAccessKey. "
                        + "Please save real Volcengine VOD credentials before testing",
                exception.getMessage()
        );
    }

    private VideoStorageConfig readyConfig() {
        VideoStorageConfig config = new VideoStorageConfig();
        config.setRegion("cn-north-1");
        config.setSpaceName("learning-space");
        config.setSecretIdEncrypted(cryptoService.encrypt("AKLTFAKEACCESSKEY"));
        config.setSecretKeyEncrypted(cryptoService.encrypt("FAKESECRETACCESSKEY"));
        return config;
    }
}

