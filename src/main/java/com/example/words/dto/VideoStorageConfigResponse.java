package com.example.words.dto;

import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageConfigStatus;
import com.example.words.model.VideoStorageProviderType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoStorageConfigResponse {

    private Long id;
    private String configName;
    private String secretIdMasked;
    private String secretKeyMasked;
    private String region;
    private VideoStorageProviderType providerType;
    private Long subAppId;
    private String spaceName;
    private String procedureName;
    private VideoStorageConfigStatus status;
    private Boolean isDefault;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VideoStorageConfigResponse from(VideoStorageConfig config) {
        return new VideoStorageConfigResponse(
                config.getId(),
                config.getConfigName(),
                config.getSecretIdMasked(),
                config.getSecretKeyMasked(),
                config.getRegion(),
                config.getProviderType(),
                config.getSubAppId(),
                config.getSpaceName(),
                config.getProcedureName(),
                config.getStatus(),
                config.getIsDefault(),
                config.getRemark(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
