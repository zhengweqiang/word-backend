package com.example.words.service;

import com.example.words.dto.CreateVideoStorageConfigRequest;
import com.example.words.dto.UpdateVideoStorageConfigRequest;
import com.example.words.dto.UpdateVideoStorageConfigStatusRequest;
import com.example.words.dto.VideoStorageConfigResponse;
import com.example.words.dto.VideoStorageConfigTestResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ConflictException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageConfigStatus;
import com.example.words.model.VideoStorageProviderType;
import com.example.words.repository.VideoAssetRepository;
import com.example.words.repository.VideoStorageConfigRepository;
import com.example.words.service.video.VideoStorageGateway;
import com.example.words.service.video.VideoStorageGatewayRegistry;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoStorageConfigService {

    private final VideoStorageConfigRepository videoStorageConfigRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final VideoStorageConfigCryptoService cryptoService;
    private final CurrentUserService currentUserService;
    private final AccessControlService accessControlService;
    private final VideoStorageGatewayRegistry gatewayRegistry;

    public VideoStorageConfigService(
            VideoStorageConfigRepository videoStorageConfigRepository,
            VideoAssetRepository videoAssetRepository,
            VideoStorageConfigCryptoService cryptoService,
            CurrentUserService currentUserService,
            AccessControlService accessControlService,
            VideoStorageGatewayRegistry gatewayRegistry) {
        this.videoStorageConfigRepository = videoStorageConfigRepository;
        this.videoAssetRepository = videoAssetRepository;
        this.cryptoService = cryptoService;
        this.currentUserService = currentUserService;
        this.accessControlService = accessControlService;
        this.gatewayRegistry = gatewayRegistry;
    }

    @Transactional(readOnly = true)
    public List<VideoStorageConfigResponse> listConfigs(VideoStorageConfigStatus status) {
        ensureAdmin();
        List<VideoStorageConfig> configs = status == null
                ? videoStorageConfigRepository.findAllByOrderByUpdatedAtDesc()
                : videoStorageConfigRepository.findByStatusOrderByUpdatedAtDesc(status);
        return configs.stream()
                .map(VideoStorageConfigResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VideoStorageConfigResponse getConfigResponse(Long id) {
        ensureAdmin();
        return VideoStorageConfigResponse.from(getConfigEntity(id));
    }

    @Transactional
    public VideoStorageConfigResponse create(CreateVideoStorageConfigRequest request) {
        ensureAdmin();
        String configName = normalizeRequiredText(request.getConfigName(), "configName", 100);
        String secretId = normalizeRequiredSecret(request.getSecretId(), "secretId");
        String secretKey = normalizeRequiredSecret(request.getSecretKey(), "secretKey");
        String region = normalizeRequiredText(request.getRegion(), "region", 64);
        VideoStorageProviderType providerType = request.getProviderType();
        ensureVolcengineProvider(providerType);
        String spaceName = normalizeSpaceName(providerType, request.getSpaceName());
        String procedureName = trimToNull(request.getProcedureName());
        String remark = trimToNull(request.getRemark());

        if (videoStorageConfigRepository.existsByConfigName(configName)) {
            throw new ConflictException("Video storage config already exists");
        }

        VideoStorageConfig config = new VideoStorageConfig();
        config.setConfigName(configName);
        config.setSecretIdEncrypted(cryptoService.encrypt(secretId));
        config.setSecretIdMasked(cryptoService.mask(secretId));
        config.setSecretKeyEncrypted(cryptoService.encrypt(secretKey));
        config.setSecretKeyMasked(cryptoService.mask(secretKey));
        config.setRegion(region);
        config.setProviderType(providerType);
        config.setSubAppId(request.getSubAppId());
        config.setSpaceName(spaceName);
        config.setProcedureName(procedureName);
        config.setStatus(request.getStatus());
        config.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
        config.setRemark(remark);

        boolean shouldAutoDefault = videoStorageConfigRepository.countByStatus(VideoStorageConfigStatus.ENABLED) == 0
                && request.getStatus() == VideoStorageConfigStatus.ENABLED;
        if (Boolean.TRUE.equals(config.getIsDefault()) || shouldAutoDefault) {
            ensureEnabled(config.getStatus());
            config.setIsDefault(Boolean.TRUE);
            videoStorageConfigRepository.clearDefault();
        }

        return saveWithConflictHandling(config);
    }

    @Transactional
    public VideoStorageConfigResponse update(Long id, UpdateVideoStorageConfigRequest request) {
        ensureAdmin();
        VideoStorageConfig config = getConfigEntity(id);

        String configName = normalizeRequiredText(request.getConfigName(), "configName", 100);
        String region = normalizeRequiredText(request.getRegion(), "region", 64);
        String secretId = normalizeOptionalSecret(request.getSecretId());
        String secretKey = normalizeOptionalSecret(request.getSecretKey());
        VideoStorageProviderType providerType = request.getProviderType();
        ensureVolcengineProvider(providerType);
        String spaceName = normalizeSpaceName(providerType, request.getSpaceName());

        if (videoStorageConfigRepository.existsByConfigNameAndIdNot(configName, id)) {
            throw new ConflictException("Video storage config already exists");
        }
        if (videoAssetRepository.countByStorageConfigId(id) > 0
                && config.getProviderType() != providerType) {
            throw new ConflictException("Video storage provider cannot be changed after videos are uploaded");
        }

        config.setConfigName(configName);
        config.setRegion(region);
        config.setProviderType(providerType);
        config.setSubAppId(request.getSubAppId());
        config.setSpaceName(spaceName);
        config.setProcedureName(trimToNull(request.getProcedureName()));
        config.setStatus(request.getStatus());
        config.setRemark(trimToNull(request.getRemark()));

        if (secretId != null) {
            config.setSecretIdEncrypted(cryptoService.encrypt(secretId));
            config.setSecretIdMasked(cryptoService.mask(secretId));
        }
        if (secretKey != null) {
            config.setSecretKeyEncrypted(cryptoService.encrypt(secretKey));
            config.setSecretKeyMasked(cryptoService.mask(secretKey));
        }

        boolean requestedDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (requestedDefault) {
            ensureEnabled(request.getStatus());
            videoStorageConfigRepository.clearDefaultByIdNot(config.getId());
            config.setIsDefault(Boolean.TRUE);
        } else if (request.getStatus() == VideoStorageConfigStatus.DISABLED || Boolean.FALSE.equals(request.getIsDefault())) {
            config.setIsDefault(Boolean.FALSE);
        }

        return saveWithConflictHandling(config);
    }

    @Transactional
    public void delete(Long id) {
        ensureAdmin();
        VideoStorageConfig config = getConfigEntity(id);
        if (videoAssetRepository.countByStorageConfigId(id) > 0) {
            throw new ConflictException("Video storage config is still referenced by uploaded videos");
        }
        videoStorageConfigRepository.delete(config);
    }

    @Transactional
    public VideoStorageConfigResponse updateStatus(Long id, UpdateVideoStorageConfigStatusRequest request) {
        ensureAdmin();
        VideoStorageConfig config = getConfigEntity(id);
        config.setStatus(request.getStatus());
        if (request.getStatus() == VideoStorageConfigStatus.DISABLED) {
            config.setIsDefault(Boolean.FALSE);
        }
        return saveWithConflictHandling(config);
    }

    @Transactional
    public VideoStorageConfigResponse setDefault(Long id) {
        ensureAdmin();
        VideoStorageConfig config = getConfigEntity(id);
        ensureVolcengineProvider(config.getProviderType());
        ensureEnabled(config.getStatus());
        videoStorageConfigRepository.clearDefaultByIdNot(id);
        config.setIsDefault(Boolean.TRUE);
        return saveWithConflictHandling(config);
    }

    @Transactional(readOnly = true)
    public VideoStorageConfigTestResponse test(Long id) {
        ensureAdmin();
        VideoStorageConfig config = getConfigEntity(id);
        ensureVolcengineProvider(config.getProviderType());
        VideoStorageGateway gateway = gatewayRegistry.get(config.getProviderType());
        gateway.validate(config);
        return new VideoStorageConfigTestResponse(
                config.getId(),
                config.getConfigName(),
                true,
                "Volcengine VOD connection succeeded"
        );
    }

    @Transactional(readOnly = true)
    public VideoStorageConfig getDefaultEnabledConfig() {
        VideoStorageConfig config = videoStorageConfigRepository.findByIsDefaultTrue()
                .orElseThrow(() -> new BadRequestException("No default video storage config is available"));
        ensureEnabled(config.getStatus());
        ensureVolcengineProvider(config.getProviderType());
        return config;
    }

    @Transactional(readOnly = true)
    public VideoStorageConfig getConfigEntity(Long id) {
        return videoStorageConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video storage config not found: " + id));
    }

    private void ensureAdmin() {
        AppUser actor = currentUserService.getCurrentUser();
        accessControlService.ensureAdmin(actor);
    }

    private VideoStorageConfigResponse saveWithConflictHandling(VideoStorageConfig config) {
        try {
            return VideoStorageConfigResponse.from(videoStorageConfigRepository.save(config));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Video storage config already exists");
        }
    }

    private void ensureEnabled(VideoStorageConfigStatus status) {
        if (status != VideoStorageConfigStatus.ENABLED) {
            throw new BadRequestException("Default video storage config must be enabled");
        }
    }

    private void ensureVolcengineProvider(VideoStorageProviderType providerType) {
        if (providerType != VideoStorageProviderType.VOLCENGINE_VOD) {
            throw new BadRequestException("Tencent VOD is deprecated. Use Volcengine VOD for video storage.");
        }
    }

    private String normalizeRequiredText(String value, String fieldName, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new BadRequestException(fieldName + " exceeds maximum length");
        }
        return normalized;
    }

    private String normalizeRequiredSecret(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeOptionalSecret(String value) {
        return trimToNull(value);
    }

    private String normalizeSpaceName(VideoStorageProviderType providerType, String value) {
        String normalized = trimToNull(value);
        if (providerType == VideoStorageProviderType.VOLCENGINE_VOD && normalized == null) {
            throw new BadRequestException("spaceName is required for Volcengine VOD");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
