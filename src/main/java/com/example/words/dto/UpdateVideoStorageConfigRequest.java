package com.example.words.dto;

import com.example.words.model.VideoStorageConfigStatus;
import com.example.words.model.VideoStorageProviderType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVideoStorageConfigRequest {

    @NotBlank(message = "configName is required")
    @Size(max = 100, message = "configName must not exceed 100 characters")
    private String configName;

    @Size(max = 512, message = "secretId must not exceed 512 characters")
    private String secretId;

    @Size(max = 512, message = "secretKey must not exceed 512 characters")
    private String secretKey;

    @NotBlank(message = "region is required")
    @Size(max = 64, message = "region must not exceed 64 characters")
    private String region;

    @Positive(message = "subAppId must be positive")
    private Long subAppId;

    @Size(max = 128, message = "procedureName must not exceed 128 characters")
    private String procedureName;

    @NotNull(message = "providerType is required")
    private VideoStorageProviderType providerType;

    @Size(max = 128, message = "spaceName must not exceed 128 characters")
    private String spaceName;

    @NotNull(message = "status is required")
    private VideoStorageConfigStatus status;

    @NotNull(message = "isDefault is required")
    private Boolean isDefault;

    @Size(max = 500, message = "remark must not exceed 500 characters")
    private String remark;

    @AssertTrue(message = "spaceName is required for Volcengine VOD")
    public boolean isVolcengineSpaceNameValid() {
        if (providerType != VideoStorageProviderType.VOLCENGINE_VOD) {
            return true;
        }
        return spaceName != null && !spaceName.trim().isEmpty();
    }
}
