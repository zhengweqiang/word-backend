package com.example.words.service;

import com.example.words.dto.VideoAccessResponse;
import com.example.words.dto.VideoResponse;
import com.example.words.dto.StudentVideoResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.ResourceScopeType;
import com.example.words.model.UserRole;
import com.example.words.model.VideoAccessMode;
import com.example.words.model.VideoAsset;
import com.example.words.model.VideoPublishStatus;
import com.example.words.model.VideoStatus;
import com.example.words.model.VideoStorageConfig;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.VideoAssetRepository;
import com.example.words.repository.VideoStorageConfigRepository;
import com.example.words.service.video.VideoMediaInfo;
import com.example.words.service.video.VideoStorageGateway;
import com.example.words.service.video.VideoStorageGatewayRegistry;
import com.example.words.service.video.VideoUploadResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VideoAssetService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp4", "mov", "m4v", "avi", "mkv", "webm");
    private static final int TRANSCODE_SYNC_MAX_ATTEMPTS = 10;
    private static final long TRANSCODE_SYNC_INTERVAL_MILLIS = 3000L;

    private final VideoAssetRepository videoAssetRepository;
    private final VideoStorageConfigRepository videoStorageConfigRepository;
    private final CurrentUserService currentUserService;
    private final AccessControlService accessControlService;
    private final VideoStorageConfigService videoStorageConfigService;
    private final VideoStorageGatewayRegistry gatewayRegistry;
    private final AppUserRepository appUserRepository;
    private final TeacherStudentService teacherStudentService;

    public VideoAssetService(
            VideoAssetRepository videoAssetRepository,
            VideoStorageConfigRepository videoStorageConfigRepository,
            CurrentUserService currentUserService,
            AccessControlService accessControlService,
            VideoStorageConfigService videoStorageConfigService,
            VideoStorageGatewayRegistry gatewayRegistry,
            AppUserRepository appUserRepository,
            TeacherStudentService teacherStudentService) {
        this.videoAssetRepository = videoAssetRepository;
        this.videoStorageConfigRepository = videoStorageConfigRepository;
        this.currentUserService = currentUserService;
        this.accessControlService = accessControlService;
        this.videoStorageConfigService = videoStorageConfigService;
        this.gatewayRegistry = gatewayRegistry;
        this.appUserRepository = appUserRepository;
        this.teacherStudentService = teacherStudentService;
    }

    @Transactional(readOnly = true)
    public Page<VideoResponse> listVisibleVideosPage(
            int page,
            int size,
            String keyword,
            VideoStatus status,
            VideoPublishStatus publishStatus,
            ResourceScopeType scopeType) {
        AppUser actor = currentUserService.getCurrentUser();
        Pageable pageable = buildPageable(page, size);
        Specification<VideoAsset> specification = Specification.<VideoAsset>where(visibleTo(actor))
                .and(keywordLike(keyword))
                .and(statusEquals(status))
                .and(publishStatusEquals(publishStatus))
                .and(scopeEquals(scopeType));

        Page<VideoAsset> pageResult = videoAssetRepository.findAll(specification, pageable);
        List<VideoResponse> content = enrichResponses(pageResult.getContent(), actor);
        return new PageImpl<>(content, pageable, pageResult.getTotalElements());
    }

    @Transactional(readOnly = true)
    public VideoResponse getVideoResponse(Long id) {
        AppUser actor = currentUserService.getCurrentUser();
        VideoAsset videoAsset = getVideoEntity(id);
        accessControlService.ensureCanViewVideo(actor, videoAsset);
        return enrichResponses(List.of(videoAsset), actor).get(0);
    }

    @Transactional(readOnly = true)
    public Page<StudentVideoResponse> listStudentVideosPage(int page, int size, String keyword) {
        AppUser actor = currentUserService.getCurrentUser();
        if (actor.getRole() != UserRole.STUDENT) {
            throw new AccessDeniedException("Only students can access published videos");
        }
        Pageable pageable = buildPageable(page, size);
        Set<Long> responsibleTeacherIds = teacherStudentService.getResponsibleTeacherIdsForStudent(actor.getId());
        Specification<VideoAsset> specification = Specification.<VideoAsset>where(studentVisibleTo(responsibleTeacherIds))
                .and(keywordLike(keyword));

        Page<VideoAsset> pageResult = videoAssetRepository.findAll(specification, pageable);
        List<StudentVideoResponse> content = enrichStudentResponses(pageResult.getContent());
        return new PageImpl<>(content, pageable, pageResult.getTotalElements());
    }

    @Transactional
    public VideoResponse upload(MultipartFile file, String title, String description) {
        AppUser actor = currentUserService.getCurrentUser();
        ensureAdminOrTeacher(actor);
        validateUpload(file);

        VideoStorageConfig storageConfig = videoStorageConfigService.getDefaultEnabledConfig();
        String originalFileName = file.getOriginalFilename() == null ? "video.mp4" : file.getOriginalFilename().trim();
        String resolvedTitle = resolveTitle(title, originalFileName);
        Path tempFile = null;

        try {
            tempFile = Files.createTempFile("vod-upload-", "." + extensionOf(originalFileName));
            file.transferTo(tempFile);

            VideoStorageGateway gateway = gatewayRegistry.get(storageConfig.getProviderType());
            VideoUploadResult uploadResult = gateway.upload(
                    storageConfig,
                    tempFile,
                    originalFileName,
                    resolvedTitle,
                    trimToNull(description)
            );

            VideoMediaInfo mediaInfo = uploadResult.transcodeRequested()
                    ? waitForPreferredPlayback(gateway, storageConfig, uploadResult.mediaId())
                    : gateway.describeMedia(storageConfig, uploadResult.mediaId());

            VideoAsset videoAsset = new VideoAsset();
            videoAsset.setTitle(resolvedTitle);
            videoAsset.setDescription(trimToNull(description));
            videoAsset.setOriginalFileName(originalFileName);
            videoAsset.setContentType(trimToNull(file.getContentType()));
            videoAsset.setFileSize(file.getSize());
            videoAsset.setTencentFileId(uploadResult.mediaId());
            videoAsset.setMediaUrl(resolvePlayableUrl(uploadResult, mediaInfo));
            videoAsset.setCoverUrl(firstNonBlank(uploadResult.coverUrl(), mediaInfo.coverUrl()));
            videoAsset.setDurationSeconds(mediaInfo.durationSeconds());
            videoAsset.setStatus(resolveStatus(storageConfig, videoAsset.getMediaUrl(), mediaInfo));
            videoAsset.setCreatedBy(actor.getId());
            videoAsset.setOwnerUserId(actor.getId());
            videoAsset.setScopeType(actor.getRole() == UserRole.ADMIN ? ResourceScopeType.SYSTEM : ResourceScopeType.TEACHER);
            videoAsset.setStorageConfigId(storageConfig.getId());

            VideoAsset saved = videoAssetRepository.save(videoAsset);
            return enrichResponses(List.of(saved), actor).get(0);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist uploaded video temporarily", ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Ignore temp cleanup failure.
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public VideoAccessResponse getAccess(Long id) {
        AppUser actor = currentUserService.getCurrentUser();
        VideoAsset videoAsset = getVideoEntity(id);
        accessControlService.ensureCanViewVideo(actor, videoAsset);
        if (videoAsset.getStatus() != VideoStatus.READY || trimToNull(videoAsset.getMediaUrl()) == null) {
            throw new BadRequestException("Video is not ready for preview");
        }
        return new VideoAccessResponse(
                videoAsset.getId(),
                VideoAccessMode.PREVIEW,
                videoAsset.getMediaUrl(),
                videoAsset.getCoverUrl()
        );
    }

    @Transactional(readOnly = true)
    public VideoAccessResponse getStudentPlayback(Long id) {
        AppUser actor = currentUserService.getCurrentUser();
        VideoAsset videoAsset = getVideoEntity(id);
        if (!isVisibleToStudent(actor, videoAsset)) {
            throw new ResourceNotFoundException("Video not found: " + id);
        }
        if (trimToNull(videoAsset.getMediaUrl()) == null) {
            throw new BadRequestException("Video is not ready for playback");
        }
        return new VideoAccessResponse(
                videoAsset.getId(),
                VideoAccessMode.PLAY,
                videoAsset.getMediaUrl(),
                videoAsset.getCoverUrl()
        );
    }

    @Transactional
    public VideoResponse sync(Long id) {
        AppUser actor = currentUserService.getCurrentUser();
        VideoAsset videoAsset = getVideoEntity(id);
        accessControlService.ensureCanManageVideo(actor, videoAsset);

        VideoStorageConfig config = videoStorageConfigService.getConfigEntity(videoAsset.getStorageConfigId());
        VideoStorageGateway gateway = gatewayRegistry.get(config.getProviderType());
        VideoMediaInfo mediaInfo = gateway.describeMedia(config, videoAsset.getTencentFileId());
        String resolvedMediaUrl = resolvePlayableUrl(config, mediaInfo, videoAsset.getMediaUrl());
        videoAsset.setMediaUrl(resolvedMediaUrl);
        videoAsset.setCoverUrl(firstNonBlank(mediaInfo.coverUrl(), videoAsset.getCoverUrl()));
        videoAsset.setDurationSeconds(mediaInfo.durationSeconds() != null
                ? mediaInfo.durationSeconds()
                : videoAsset.getDurationSeconds());
        videoAsset.setStatus(resolveStatus(config, resolvedMediaUrl, mediaInfo));
        videoAsset.setErrorMessage(null);

        return enrichResponses(List.of(videoAssetRepository.save(videoAsset)), actor).get(0);
    }

    @Transactional
    public VideoResponse publish(Long id) {
        AppUser actor = currentUserService.getCurrentUser();
        VideoAsset videoAsset = getVideoEntity(id);
        accessControlService.ensureCanManageVideo(actor, videoAsset);
        if (videoAsset.getStatus() != VideoStatus.READY || trimToNull(videoAsset.getMediaUrl()) == null) {
            throw new BadRequestException("Video must be ready before publishing");
        }
        if (videoAsset.getPublishStatus() != VideoPublishStatus.PUBLISHED) {
            videoAsset.setPublishStatus(VideoPublishStatus.PUBLISHED);
            videoAsset.setPublishedAt(LocalDateTime.now());
        }
        return enrichResponses(List.of(videoAssetRepository.save(videoAsset)), actor).get(0);
    }

    @Transactional
    public VideoResponse unpublish(Long id) {
        AppUser actor = currentUserService.getCurrentUser();
        VideoAsset videoAsset = getVideoEntity(id);
        accessControlService.ensureCanManageVideo(actor, videoAsset);
        if (videoAsset.getPublishStatus() != VideoPublishStatus.UNPUBLISHED) {
            videoAsset.setPublishStatus(VideoPublishStatus.UNPUBLISHED);
            videoAsset.setUnpublishedAt(LocalDateTime.now());
        }
        return enrichResponses(List.of(videoAssetRepository.save(videoAsset)), actor).get(0);
    }

    @Transactional
    public void delete(Long id) {
        AppUser actor = currentUserService.getCurrentUser();
        VideoAsset videoAsset = getVideoEntity(id);
        accessControlService.ensureCanManageVideo(actor, videoAsset);

        VideoStorageConfig config = videoStorageConfigService.getConfigEntity(videoAsset.getStorageConfigId());
        VideoStorageGateway gateway = gatewayRegistry.get(config.getProviderType());
        gateway.deleteMedia(config, videoAsset.getTencentFileId());
        videoAssetRepository.delete(videoAsset);
    }

    @Transactional(readOnly = true)
    public VideoAsset getVideoEntity(Long id) {
        return videoAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found: " + id));
    }

    private List<VideoResponse> enrichResponses(List<VideoAsset> videos, AppUser actor) {
        Set<Long> userIds = videos.stream()
                .map(VideoAsset::getCreatedBy)
                .collect(Collectors.toSet());
        Set<Long> configIds = videos.stream()
                .map(VideoAsset::getStorageConfigId)
                .collect(Collectors.toSet());

        Map<Long, String> userNames = appUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
        Map<Long, String> configNames = videoStorageConfigRepository.findAllById(configIds).stream()
                .collect(Collectors.toMap(VideoStorageConfig::getId, VideoStorageConfig::getConfigName));

        return videos.stream()
                .map(video -> toResponse(video, actor, userNames, configNames))
                .toList();
    }

    private List<StudentVideoResponse> enrichStudentResponses(List<VideoAsset> videos) {
        Set<Long> userIds = videos.stream()
                .map(VideoAsset::getCreatedBy)
                .collect(Collectors.toSet());
        Map<Long, String> userNames = appUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));

        return videos.stream()
                .map(video -> new StudentVideoResponse(
                        video.getId(),
                        video.getTitle(),
                        video.getDescription(),
                        video.getCoverUrl(),
                        video.getDurationSeconds(),
                        userNames.getOrDefault(video.getCreatedBy(), "用户#" + video.getCreatedBy()),
                        video.getPublishedAt(),
                        video.getUpdatedAt()
                ))
                .toList();
    }

    private VideoResponse toResponse(
            VideoAsset video,
            AppUser actor,
            Map<Long, String> userNames,
            Map<Long, String> configNames) {
        boolean canManage = canManage(actor, video);
        boolean canPreview = video.getStatus() == VideoStatus.READY && trimToNull(video.getMediaUrl()) != null;
        return new VideoResponse(
                video.getId(),
                video.getTitle(),
                video.getDescription(),
                video.getOriginalFileName(),
                video.getContentType(),
                video.getFileSize(),
                video.getTencentFileId(),
                video.getMediaUrl(),
                video.getCoverUrl(),
                video.getDurationSeconds(),
                video.getStatus(),
                video.getPublishStatus(),
                video.getErrorMessage(),
                video.getCreatedBy(),
                userNames.getOrDefault(video.getCreatedBy(), "用户#" + video.getCreatedBy()),
                video.getOwnerUserId(),
                video.getScopeType(),
                video.getStorageConfigId(),
                configNames.get(video.getStorageConfigId()),
                canManage,
                canPreview,
                video.getPublishedAt(),
                video.getUnpublishedAt(),
                video.getCreatedAt(),
                video.getUpdatedAt()
        );
    }

    private boolean canManage(AppUser actor, VideoAsset video) {
        if (actor.getRole() == UserRole.ADMIN) {
            return true;
        }
        return actor.getRole() == UserRole.TEACHER
                && video.getScopeType() != ResourceScopeType.SYSTEM
                && actor.getId().equals(video.getOwnerUserId());
    }

    private Pageable buildPageable(int page, int size) {
        int normalizedPage = Math.max(page, 1) - 1;
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private Specification<VideoAsset> visibleTo(AppUser actor) {
        if (actor.getRole() == UserRole.ADMIN) {
            return null;
        }
        if (actor.getRole() == UserRole.TEACHER) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.equal(root.get("scopeType"), ResourceScopeType.SYSTEM),
                    criteriaBuilder.equal(root.get("ownerUserId"), actor.getId()),
                    criteriaBuilder.equal(root.get("createdBy"), actor.getId())
            );
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.disjunction();
    }

    private Specification<VideoAsset> studentVisibleTo(Set<Long> responsibleTeacherIds) {
        return (root, query, criteriaBuilder) -> {
            var ready = criteriaBuilder.equal(root.get("status"), VideoStatus.READY);
            var published = criteriaBuilder.equal(root.get("publishStatus"), VideoPublishStatus.PUBLISHED);
            var systemScope = criteriaBuilder.equal(root.get("scopeType"), ResourceScopeType.SYSTEM);
            if (responsibleTeacherIds == null || responsibleTeacherIds.isEmpty()) {
                return criteriaBuilder.and(ready, published, systemScope);
            }
            return criteriaBuilder.and(
                    ready,
                    published,
                    criteriaBuilder.or(systemScope, root.get("ownerUserId").in(responsibleTeacherIds))
            );
        };
    }

    private boolean isVisibleToStudent(AppUser actor, VideoAsset videoAsset) {
        if (actor.getRole() != UserRole.STUDENT
                || videoAsset.getStatus() != VideoStatus.READY
                || videoAsset.getPublishStatus() != VideoPublishStatus.PUBLISHED) {
            return false;
        }
        if (videoAsset.getScopeType() == ResourceScopeType.SYSTEM) {
            return true;
        }
        return videoAsset.getScopeType() == ResourceScopeType.TEACHER
                && teacherStudentService.isTeacherResponsibleForStudent(videoAsset.getOwnerUserId(), actor.getId());
    }

    private Specification<VideoAsset> keywordLike(String keyword) {
        String normalized = trimToNull(keyword);
        if (normalized == null) {
            return null;
        }
        String pattern = "%" + normalized.toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("originalFileName")), pattern)
        );
    }

    private Specification<VideoAsset> statusEquals(VideoStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status);
    }

    private Specification<VideoAsset> publishStatusEquals(VideoPublishStatus publishStatus) {
        if (publishStatus == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("publishStatus"), publishStatus);
    }

    private Specification<VideoAsset> scopeEquals(ResourceScopeType scopeType) {
        if (scopeType == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("scopeType"), scopeType);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Video file is required");
        }
        String originalFileName = file.getOriginalFilename();
        String extension = extensionOf(originalFileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported video file type: " + extension);
        }
        String contentType = trimToNull(file.getContentType());
        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("video/")) {
            throw new BadRequestException("Uploaded file is not recognized as a video");
        }
    }

    private void ensureAdminOrTeacher(AppUser actor) {
        if (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.TEACHER) {
            throw new AccessDeniedException("Only admin or teacher can manage videos");
        }
    }

    private String resolveTitle(String title, String originalFileName) {
        String normalized = trimToNull(title);
        if (normalized != null) {
            return normalized;
        }
        int index = originalFileName.lastIndexOf('.');
        return index > 0 ? originalFileName.substring(0, index) : originalFileName;
    }

    private VideoStatus resolveStatus(VideoStorageConfig config, String mediaUrl, VideoMediaInfo mediaInfo) {
        if (expectsManaged360pPlayback(config)) {
            return mediaInfo.preferredPlaybackReady() ? VideoStatus.READY : VideoStatus.PROCESSING;
        }
        return mediaInfo.ready() || trimToNull(mediaUrl) != null ? VideoStatus.READY : VideoStatus.PROCESSING;
    }

    private String resolvePlayableUrl(VideoUploadResult uploadResult, VideoMediaInfo mediaInfo) {
        if (uploadResult.transcodeRequested()) {
            return mediaInfo.preferredPlaybackReady() ? trimToNull(mediaInfo.mediaUrl()) : null;
        }
        return firstNonBlank(uploadResult.mediaUrl(), mediaInfo.mediaUrl());
    }

    private String resolvePlayableUrl(VideoStorageConfig config, VideoMediaInfo mediaInfo, String existingMediaUrl) {
        if (expectsManaged360pPlayback(config)) {
            return mediaInfo.preferredPlaybackReady() ? trimToNull(mediaInfo.mediaUrl()) : null;
        }
        return firstNonBlank(mediaInfo.mediaUrl(), existingMediaUrl);
    }

    private boolean expectsManaged360pPlayback(VideoStorageConfig config) {
        return trimToNull(config.getProcedureName()) == null;
    }

    private VideoMediaInfo waitForPreferredPlayback(
            VideoStorageGateway gateway,
            VideoStorageConfig storageConfig,
            String fileId) {
        VideoMediaInfo latest = null;
        for (int index = 0; index < TRANSCODE_SYNC_MAX_ATTEMPTS; index++) {
            latest = gateway.describeMedia(storageConfig, fileId);
            if (latest.preferredPlaybackReady()) {
                return latest;
            }
            if (index < TRANSCODE_SYNC_MAX_ATTEMPTS - 1) {
                sleepQuietly(TRANSCODE_SYNC_INTERVAL_MILLIS);
            }
        }
        return latest == null
                ? new VideoMediaInfo(fileId, null, null, null, false, false)
                : latest;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Tencent VOD transcode result", ex);
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String primary, String secondary) {
        String normalizedPrimary = trimToNull(primary);
        return normalizedPrimary != null ? normalizedPrimary : trimToNull(secondary);
    }
}
