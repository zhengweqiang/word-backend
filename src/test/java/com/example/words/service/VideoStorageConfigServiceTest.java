package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.CreateVideoStorageConfigRequest;
import com.example.words.dto.UpdateVideoStorageConfigRequest;
import com.example.words.dto.UpdateVideoStorageConfigStatusRequest;
import com.example.words.dto.VideoStorageConfigResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.UserRole;
import com.example.words.model.UserStatus;
import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageConfigStatus;
import com.example.words.model.VideoStorageProviderType;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.VideoAssetRepository;
import com.example.words.repository.VideoStorageConfigRepository;
import com.example.words.security.AuthenticatedUser;
import com.example.words.service.video.VideoStorageGateway;
import com.example.words.service.video.VideoStorageGatewayRegistry;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class VideoStorageConfigServiceTest {

    @Mock
    private VideoStorageConfigRepository videoStorageConfigRepository;

    @Mock
    private VideoAssetRepository videoAssetRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private VideoStorageGateway tencentStorageGateway;

    @Mock
    private VideoStorageGateway volcengineStorageGateway;

    private VideoStorageGatewayRegistry gatewayRegistry;

    private VideoStorageConfigCryptoService cryptoService;
    private CurrentUserService currentUserService;
    private AccessControlService accessControlService;
    private VideoStorageConfigService videoStorageConfigService;

    @BeforeEach
    void setUp() {
        cryptoService = new VideoStorageConfigCryptoService(
                Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes())
        );
        currentUserService = new CurrentUserService(appUserRepository);
        accessControlService = new AccessControlService(null, null, null);
        when(tencentStorageGateway.providerType()).thenReturn(VideoStorageProviderType.TENCENT_VOD);
        when(volcengineStorageGateway.providerType()).thenReturn(VideoStorageProviderType.VOLCENGINE_VOD);
        gatewayRegistry = new VideoStorageGatewayRegistry(List.of(tencentStorageGateway, volcengineStorageGateway));
        videoStorageConfigService = new VideoStorageConfigService(
                videoStorageConfigRepository,
                videoAssetRepository,
                cryptoService,
                currentUserService,
                accessControlService,
                gatewayRegistry
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createShouldAutoPromoteFirstEnabledVolcengineConfigToDefault() {
        AppUser actor = admin();
        CreateVideoStorageConfigRequest request = new CreateVideoStorageConfigRequest(
                "火山云点播",
                "secret-id-12345678",
                "secret-key-12345678",
                "cn-north-1",
                123456L,
                "vod-procedure",
                VideoStorageProviderType.VOLCENGINE_VOD,
                "learning-space",
                VideoStorageConfigStatus.ENABLED,
                Boolean.FALSE,
                "default"
        );

        authenticate(actor);
        when(videoStorageConfigRepository.existsByConfigName("火山云点播")).thenReturn(false);
        when(videoStorageConfigRepository.countByStatus(VideoStorageConfigStatus.ENABLED)).thenReturn(0L);
        when(videoStorageConfigRepository.save(any(VideoStorageConfig.class))).thenAnswer(invocation -> {
            VideoStorageConfig config = invocation.getArgument(0);
            config.setId(15L);
            return config;
        });

        VideoStorageConfigResponse response = videoStorageConfigService.create(request);

        assertEquals(15L, response.getId());
        assertTrue(response.getIsDefault());
        assertTrue(response.getSecretIdMasked().contains("****"));
        assertTrue(response.getSecretKeyMasked().contains("****"));
        verify(videoStorageConfigRepository).clearDefault();
    }

    @Test
    void createShouldRejectTencentVodConfig() {
        AppUser actor = admin();
        CreateVideoStorageConfigRequest request = new CreateVideoStorageConfigRequest(
                "腾讯云广州",
                "secret-id-12345678",
                "secret-key-12345678",
                "ap-guangzhou",
                123456L,
                "vod-procedure",
                VideoStorageProviderType.TENCENT_VOD,
                null,
                VideoStorageConfigStatus.ENABLED,
                Boolean.FALSE,
                "legacy"
        );

        authenticate(actor);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> videoStorageConfigService.create(request));

        assertEquals("Tencent VOD is deprecated. Use Volcengine VOD for video storage.", exception.getMessage());
    }

    @Test
    void updateShouldKeepExistingSecretsWhenBlank() {
        AppUser actor = admin();
        VideoStorageConfig existing = existingConfig();
        UpdateVideoStorageConfigRequest request = new UpdateVideoStorageConfigRequest(
                "火山云点播",
                "   ",
                "",
                "cn-north-1",
                223344L,
                "procedure-b",
                VideoStorageProviderType.VOLCENGINE_VOD,
                "learning-space",
                VideoStorageConfigStatus.ENABLED,
                Boolean.TRUE,
                "updated"
        );

        authenticate(actor);
        when(videoStorageConfigRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(videoStorageConfigRepository.existsByConfigNameAndIdNot("火山云点播", existing.getId())).thenReturn(false);
        when(videoStorageConfigRepository.save(any(VideoStorageConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VideoStorageConfigResponse response = videoStorageConfigService.update(existing.getId(), request);

        assertEquals("v1:secret-id", existing.getSecretIdEncrypted());
        assertEquals("v1:secret-key", existing.getSecretKeyEncrypted());
        assertEquals("cn-north-1", response.getRegion());
        verify(videoStorageConfigRepository).clearDefaultByIdNot(existing.getId());
    }

    @Test
    void updateStatusShouldClearDefaultWhenDisabled() {
        AppUser actor = admin();
        VideoStorageConfig existing = existingConfig();
        existing.setIsDefault(Boolean.TRUE);

        authenticate(actor);
        when(videoStorageConfigRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(videoStorageConfigRepository.save(any(VideoStorageConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VideoStorageConfigResponse response = videoStorageConfigService.updateStatus(
                existing.getId(),
                new UpdateVideoStorageConfigStatusRequest(VideoStorageConfigStatus.DISABLED)
        );

        assertEquals(VideoStorageConfigStatus.DISABLED, response.getStatus());
        assertFalse(response.getIsDefault());
    }

    @Test
    void setDefaultShouldClearOtherDefaultFlagsFirst() {
        AppUser actor = admin();
        VideoStorageConfig existing = existingConfig();

        authenticate(actor);
        when(videoStorageConfigRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(videoStorageConfigRepository.save(any(VideoStorageConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        videoStorageConfigService.setDefault(existing.getId());

        ArgumentCaptor<VideoStorageConfig> captor = ArgumentCaptor.forClass(VideoStorageConfig.class);
        verify(videoStorageConfigRepository).save(captor.capture());
        verify(videoStorageConfigRepository).clearDefaultByIdNot(existing.getId());
        assertTrue(captor.getValue().getIsDefault());
    }

    @Test
    void testShouldUseConfigProviderGateway() {
        AppUser actor = admin();
        VideoStorageConfig existing = existingConfig();
        existing.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);

        authenticate(actor);
        when(videoStorageConfigRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        videoStorageConfigService.test(existing.getId());

        verify(volcengineStorageGateway).validate(existing);
    }

    private AppUser admin() {
        AppUser actor = new AppUser();
        actor.setId(1L);
        actor.setUsername("admin");
        actor.setPasswordHash("hashed");
        actor.setDisplayName("管理员");
        actor.setRole(UserRole.ADMIN);
        actor.setStatus(UserStatus.ACTIVE);
        return actor;
    }

    private VideoStorageConfig existingConfig() {
        VideoStorageConfig config = new VideoStorageConfig();
        config.setId(9L);
        config.setConfigName("火山云点播");
        config.setSecretIdEncrypted("v1:secret-id");
        config.setSecretIdMasked("id****mask");
        config.setSecretKeyEncrypted("v1:secret-key");
        config.setSecretKeyMasked("key****mask");
        config.setRegion("cn-north-1");
        config.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);
        config.setSpaceName("learning-space");
        config.setStatus(VideoStorageConfigStatus.ENABLED);
        config.setIsDefault(Boolean.FALSE);
        return config;
    }

    private void authenticate(AppUser actor) {
        when(appUserRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.from(actor),
                actor.getPasswordHash(),
                AuthenticatedUser.from(actor).getAuthorities()
        ));
    }
}
