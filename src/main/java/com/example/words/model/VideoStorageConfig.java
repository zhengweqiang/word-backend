package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "video_storage_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class VideoStorageConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_name", nullable = false, length = 100)
    private String configName;

    @Column(name = "secret_id_encrypted", nullable = false, columnDefinition = "TEXT")
    private String secretIdEncrypted;

    @Column(name = "secret_id_masked", nullable = false, length = 64)
    private String secretIdMasked;

    @Column(name = "secret_key_encrypted", nullable = false, columnDefinition = "TEXT")
    private String secretKeyEncrypted;

    @Column(name = "secret_key_masked", nullable = false, length = 64)
    private String secretKeyMasked;

    @Column(name = "region", nullable = false, length = 64)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private VideoStorageProviderType providerType = VideoStorageProviderType.VOLCENGINE_VOD;

    @Column(name = "sub_app_id")
    private Long subAppId;

    @Column(name = "space_name", length = 128)
    private String spaceName;

    @Column(name = "procedure_name", length = 128)
    private String procedureName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VideoStorageConfigStatus status = VideoStorageConfigStatus.DISABLED;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    @Column(name = "remark", length = 500)
    private String remark;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
