package com.example.words.repository;

import com.example.words.model.VideoAsset;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoAssetRepository extends JpaRepository<VideoAsset, Long>, JpaSpecificationExecutor<VideoAsset> {

    long countByStorageConfigId(Long storageConfigId);

    Optional<VideoAsset> findByTencentFileId(String tencentFileId);
}
