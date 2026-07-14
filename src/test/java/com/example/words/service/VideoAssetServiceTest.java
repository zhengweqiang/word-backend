package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.VideoCloudSyncResponse;
import com.example.words.dto.VideoAccessResponse;
import com.example.words.dto.VideoResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.ResourceScopeType;
import com.example.words.model.UserRole;
import com.example.words.model.UserStatus;
import com.example.words.model.VideoAccessMode;
import com.example.words.model.VideoAsset;
import com.example.words.model.VideoCloudPublishStatus;
import com.example.words.model.VideoStatus;
import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageProviderType;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.VideoAssetRepository;
import com.example.words.repository.VideoStorageConfigRepository;
import com.example.words.security.AuthenticatedUser;
import com.example.words.service.video.VideoCloudMediaItem;
import com.example.words.service.video.VideoMediaInfo;
import com.example.words.service.video.VideoStorageGateway;
import com.example.words.service.video.VideoStorageGatewayRegistry;
import com.example.words.service.video.VideoUploadResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class VideoAssetServiceTest {

    @Mock
    private VideoAssetRepository videoAssetRepository;

    @Mock
    private VideoStorageConfigRepository videoStorageConfigRepository;

    @Mock
    private VideoStorageGateway tencentStorageGateway;

    @Mock
    private VideoStorageGateway volcengineStorageGateway;

    private VideoStorageGatewayRegistry gatewayRegistry;

    @Mock
    private AppUserRepository appUserRepository;

    private TeacherStudentService teacherStudentService;
    private Set<Long> responsibleTeacherIds;

    private CurrentUserService currentUserService;
    private AccessControlService accessControlService;
    private VideoStorageConfigService videoStorageConfigService;
    private VideoAssetService videoAssetService;
    private VideoStorageConfig defaultStorageConfig;
    private VideoStorageConfig resolvedStorageConfig;

    @BeforeEach
    void setUp() {
        currentUserService = new CurrentUserService(appUserRepository);
        accessControlService = new AccessControlService(null, null, null);
        when(tencentStorageGateway.providerType()).thenReturn(VideoStorageProviderType.TENCENT_VOD);
        when(volcengineStorageGateway.providerType()).thenReturn(VideoStorageProviderType.VOLCENGINE_VOD);
        gatewayRegistry = new VideoStorageGatewayRegistry(List.of(tencentStorageGateway, volcengineStorageGateway));
        videoStorageConfigService = new VideoStorageConfigService(null, null, null, null, null, null) {
            @Override
            public VideoStorageConfig getDefaultEnabledConfig() {
                return defaultStorageConfig;
            }

            @Override
            public VideoStorageConfig getConfigEntity(Long id) {
                return resolvedStorageConfig;
            }
        };
        responsibleTeacherIds = Set.of();
        teacherStudentService = new TeacherStudentService(null, null, null, null, null) {
            @Override
            public Set<Long> getResponsibleTeacherIdsForStudent(Long studentId) {
                return responsibleTeacherIds;
            }

            @Override
            public boolean isTeacherResponsibleForStudent(Long teacherId, Long studentId) {
                return responsibleTeacherIds.contains(teacherId);
            }
        };
        videoAssetService = new VideoAssetService(
                videoAssetRepository,
                videoStorageConfigRepository,
                currentUserService,
                accessControlService,
                videoStorageConfigService,
                gatewayRegistry,
                appUserRepository,
                teacherStudentService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadShouldSaveTeacherVideoAndReturnPreviewableResponse() {
        AppUser actor = teacher();
        VideoStorageConfig config = defaultConfig();
        defaultStorageConfig = config;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lesson.mp4",
                "video/mp4",
                "video-bytes".getBytes()
        );

        authenticate(actor);
        when(volcengineStorageGateway.upload(any(), any(), any(), any(), any())).thenReturn(
                new VideoUploadResult(
                        "file-123",
                        "https://vod.example.com/lesson.mp4",
                        "https://vod.example.com/cover.jpg",
                        false
                )
        );
        when(volcengineStorageGateway.describeMedia(config, "file-123")).thenReturn(
                new VideoMediaInfo(
                        "file-123",
                        "https://vod.example.com/lesson.mp4",
                        "https://vod.example.com/cover.jpg",
                        120L,
                        true,
                        true,
                        true,
                        null
                )
        );
        when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> {
            VideoAsset asset = invocation.getArgument(0);
            asset.setId(55L);
            return asset;
        });
        when(appUserRepository.findAllById(any())).thenReturn(List.of(actor));
        when(videoStorageConfigRepository.findAllById(any())).thenReturn(List.of(config));

        VideoResponse response = videoAssetService.upload(file, "教学视频", "第一课");

        assertEquals(55L, response.getId());
        assertEquals(ResourceScopeType.TEACHER, response.getScopeType());
        assertEquals(VideoStatus.READY, response.getStatus());
        assertEquals(VideoCloudPublishStatus.PUBLISHED, response.getCloudPublishStatus());
        assertTrue(response.isCanManage());
        verify(volcengineStorageGateway).upload(any(), any(), any(), any(), any());
    }

    @Test
    void uploadShouldMarkTeacherVideoReadyAndPublishedWhenCloudReportsProcessing() {
        AppUser actor = teacher();
        VideoStorageConfig config = defaultConfig();
        defaultStorageConfig = config;

        authenticate(actor);
        stubUploadWithProcessingCloudMetadata(config, actor);

        VideoResponse response = videoAssetService.upload(videoFile(), "Teacher lesson", "Description");

        assertEquals(ResourceScopeType.TEACHER, response.getScopeType());
        assertEquals(VideoStatus.READY, response.getStatus());
        assertEquals(VideoCloudPublishStatus.PUBLISHED, response.getCloudPublishStatus());
        assertTrue(response.getPublishedAt() != null);
        assertTrue(response.isCanManage());
    }

    @Test
    void uploadShouldMarkAdminSystemVideoReadyAndPublishedWhenCloudReportsProcessing() {
        AppUser actor = admin();
        VideoStorageConfig config = defaultConfig();
        defaultStorageConfig = config;

        authenticate(actor);
        stubUploadWithProcessingCloudMetadata(config, actor);

        VideoResponse response = videoAssetService.upload(videoFile(), "Admin lesson", "Description");

        assertEquals(ResourceScopeType.SYSTEM, response.getScopeType());
        assertEquals(VideoStatus.READY, response.getStatus());
        assertEquals(VideoCloudPublishStatus.PUBLISHED, response.getCloudPublishStatus());
        assertTrue(response.getPublishedAt() != null);
        assertTrue(response.isCanManage());
    }

    @Test
    void publishShouldPublishCloudMediaBeforeMarkingReadyVideoAsPublished() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        VideoStorageConfig config = defaultConfig();
        config.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);
        resolvedStorageConfig = config;

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findAllById(any())).thenReturn(List.of(actor));
        when(videoStorageConfigRepository.findAllById(any())).thenReturn(List.of(config));

        VideoResponse response = videoAssetService.publish(asset.getId());

        assertEquals(VideoCloudPublishStatus.PUBLISHED, response.getCloudPublishStatus());
        assertTrue(asset.getPublishedAt() != null);
        verify(volcengineStorageGateway).publishMedia(config, "file-123");
    }

    @Test
    void publishShouldSyncPlayableUrlAfterCloudPublishForProcessingVideo() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        VideoStorageConfig config = defaultConfig();
        config.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);
        resolvedStorageConfig = config;
        asset.setStatus(VideoStatus.PROCESSING);
        asset.setMediaUrl(null);

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(volcengineStorageGateway.describeMedia(config, "file-123")).thenReturn(
                new VideoMediaInfo(
                        "file-123",
                        "https://volc.example.com/video.mp4",
                        "https://volc.example.com/cover.jpg",
                        90L,
                        true,
                        true
                )
        );
        when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findAllById(any())).thenReturn(List.of(actor));
        when(videoStorageConfigRepository.findAllById(any())).thenReturn(List.of(config));

        VideoResponse response = videoAssetService.publish(asset.getId());

        assertEquals(VideoCloudPublishStatus.PUBLISHED, response.getCloudPublishStatus());
        assertEquals(VideoStatus.READY, response.getStatus());
        assertEquals("https://volc.example.com/video.mp4", response.getMediaUrl());
        verify(volcengineStorageGateway).publishMedia(config, "file-123");
        verify(volcengineStorageGateway).describeMedia(config, "file-123");
    }

    @Test
    void publishShouldRejectWithPlaybackUnavailableReason() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        VideoStorageConfig config = defaultConfig();
        config.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);
        resolvedStorageConfig = config;
        asset.setStatus(VideoStatus.PROCESSING);
        asset.setMediaUrl(null);

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(volcengineStorageGateway.describeMedia(config, "file-123")).thenReturn(
                new VideoMediaInfo(
                        "file-123",
                        null,
                        "https://volc.example.com/cover.jpg",
                        90L,
                        false,
                        false,
                        true,
                        "火山云点播未配置有效播放域名，无法获取播放地址"
                )
        );

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> videoAssetService.publish(asset.getId())
        );

        assertEquals("火山云点播未配置有效播放域名，无法获取播放地址", exception.getMessage());
        verify(volcengineStorageGateway).publishMedia(config, "file-123");
        verify(volcengineStorageGateway).describeMedia(config, "file-123");
    }

    @Test
    void publishShouldRejectVideoWithoutCloudMediaId() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        asset.setTencentFileId(null);

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        assertThrows(BadRequestException.class, () -> videoAssetService.publish(asset.getId()));
    }

    @Test
    void unpublishShouldUnpublishCloudMediaBeforeMarkingVideoAsUnpublished() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        asset.setCloudPublishStatus(VideoCloudPublishStatus.PUBLISHED);
        VideoStorageConfig config = defaultConfig();
        config.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);
        resolvedStorageConfig = config;

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findAllById(any())).thenReturn(List.of(actor));
        when(videoStorageConfigRepository.findAllById(any())).thenReturn(List.of(config));

        VideoResponse response = videoAssetService.unpublish(asset.getId());

        assertEquals(VideoCloudPublishStatus.UNPUBLISHED, response.getCloudPublishStatus());
        assertTrue(asset.getUnpublishedAt() != null);
        verify(volcengineStorageGateway).unpublishMedia(config, "file-123");
    }

    @Test
    void syncShouldUseVideoStorageConfigProvider() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        VideoStorageConfig config = defaultConfig();
        config.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);
        resolvedStorageConfig = config;

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(volcengineStorageGateway.describeMedia(config, "file-123")).thenReturn(
                new VideoMediaInfo(
                        "file-123",
                        "https://volc.example.com/video.mp4",
                        "https://volc.example.com/cover.jpg",
                        90L,
                        true,
                        true
                )
        );
        when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findAllById(any())).thenReturn(List.of(actor));
        when(videoStorageConfigRepository.findAllById(any())).thenReturn(List.of(config));

        VideoResponse response = videoAssetService.sync(asset.getId());

        assertEquals("https://volc.example.com/video.mp4", response.getMediaUrl());
        verify(volcengineStorageGateway).describeMedia(config, "file-123");
    }

    @Test
    void syncShouldStoreVolcenginePublishStatusAndPlaybackUnavailableReason() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        asset.setStatus(VideoStatus.PROCESSING);
        asset.setCloudPublishStatus(VideoCloudPublishStatus.UNPUBLISHED);
        asset.setMediaUrl(null);
        VideoStorageConfig config = defaultConfig();
        config.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);
        resolvedStorageConfig = config;

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(volcengineStorageGateway.describeMedia(config, "file-123")).thenReturn(
                new VideoMediaInfo(
                        "file-123",
                        null,
                        "https://volc.example.com/cover.jpg",
                        90L,
                        false,
                        false,
                        true,
                        "火山云点播未配置有效播放域名，无法获取播放地址"
                )
        );
        when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findAllById(any())).thenReturn(List.of(actor));
        when(videoStorageConfigRepository.findAllById(any())).thenReturn(List.of(config));

        VideoResponse response = videoAssetService.sync(asset.getId());

        assertEquals(VideoCloudPublishStatus.PUBLISHED, response.getCloudPublishStatus());
        assertEquals(VideoStatus.PROCESSING, response.getStatus());
        assertEquals("火山云点播未配置有效播放域名，无法获取播放地址", response.getErrorMessage());
        assertTrue(asset.getPublishedAt() != null);
    }

    @Test
    void syncShouldKeepLocallyPublishedAdminVideoReadyWhenCloudReportsProcessing() {
        AppUser actor = admin();
        VideoAsset asset = video();
        asset.setCreatedBy(actor.getId());
        asset.setOwnerUserId(actor.getId());
        asset.setScopeType(ResourceScopeType.SYSTEM);
        asset.setCloudPublishStatus(VideoCloudPublishStatus.PUBLISHED);
        asset.setPublishedAt(java.time.LocalDateTime.now());
        VideoStorageConfig config = defaultConfig();
        config.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);
        resolvedStorageConfig = config;

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(volcengineStorageGateway.describeMedia(config, "file-123")).thenReturn(
                new VideoMediaInfo(
                        "file-123",
                        "https://volc.example.com/video.mp4",
                        "https://volc.example.com/cover.jpg",
                        90L,
                        false,
                        false,
                        false,
                        null
                )
        );
        when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findAllById(any())).thenReturn(List.of(actor));
        when(videoStorageConfigRepository.findAllById(any())).thenReturn(List.of(config));

        VideoResponse response = videoAssetService.sync(asset.getId());

        assertEquals(VideoStatus.READY, response.getStatus());
        assertEquals(VideoCloudPublishStatus.PUBLISHED, response.getCloudPublishStatus());
        assertTrue(response.getPublishedAt() != null);
    }

    @Test
    void syncShouldMarkVideoFailedAndClearUrlWhenCloudMediaIsMissing() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        asset.setCloudPublishStatus(VideoCloudPublishStatus.PUBLISHED);
        VideoStorageConfig config = defaultConfig();
        config.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);
        resolvedStorageConfig = config;

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(volcengineStorageGateway.describeMedia(config, "file-123")).thenReturn(
                new VideoMediaInfo(
                        "file-123",
                        null,
                        null,
                        null,
                        false,
                        false,
                        false,
                        true,
                        "云端视频媒资不存在或已被删除"
                )
        );
        when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findAllById(any())).thenReturn(List.of(actor));
        when(videoStorageConfigRepository.findAllById(any())).thenReturn(List.of(config));

        VideoResponse response = videoAssetService.sync(asset.getId());

        assertEquals(VideoStatus.FAILED, response.getStatus());
        assertEquals(VideoCloudPublishStatus.UNPUBLISHED, response.getCloudPublishStatus());
        assertEquals(null, response.getMediaUrl());
        assertEquals("云端视频媒资不存在或已被删除", response.getErrorMessage());
        assertTrue(asset.getUnpublishedAt() != null);
    }

    @Test
    void deleteShouldUseVideoStorageConfigProvider() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        VideoStorageConfig config = defaultConfig();
        config.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);
        resolvedStorageConfig = config;

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        videoAssetService.delete(asset.getId());

        verify(volcengineStorageGateway).deleteMedia(config, "file-123");
        verify(videoAssetRepository).delete(asset);
    }

    @Test
    void syncDefaultCloudSpaceShouldImportMissingVideosAndUpdateExistingOnes() {
        AppUser actor = admin();
        VideoStorageConfig config = defaultConfig();
        config.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);
        defaultStorageConfig = config;
        VideoAsset existing = video();
        existing.setTencentFileId("vid-existing");
        existing.setTitle("旧标题");

        authenticate(actor);
        when(volcengineStorageGateway.listMedia(config, 0, 100)).thenReturn(List.of(
                new VideoCloudMediaItem(
                        "vid-new",
                        "云端新视频",
                        "从火山同步",
                        "cloud-new.mp4",
                        "video/mp4",
                        2048L,
                        "https://volc.example.com/cloud-new.mp4",
                        "https://volc.example.com/cloud-new.jpg",
                        88L,
                        true,
                        true
                ),
                new VideoCloudMediaItem(
                        "vid-existing",
                        "云端新标题",
                        null,
                        "cloud-existing.mp4",
                        "video/mp4",
                        4096L,
                        "https://volc.example.com/cloud-existing.mp4",
                        null,
                        120L,
                        true,
                        true
                )
        ));
        when(videoAssetRepository.findByTencentFileId("vid-new")).thenReturn(Optional.empty());
        when(videoAssetRepository.findByTencentFileId("vid-existing")).thenReturn(Optional.of(existing));
        when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VideoCloudSyncResponse response = videoAssetService.syncDefaultCloudSpace();

        assertEquals(2, response.getScanned());
        assertEquals(1, response.getImported());
        assertEquals(1, response.getUpdated());
        ArgumentCaptor<VideoAsset> captor = ArgumentCaptor.forClass(VideoAsset.class);
        verify(videoAssetRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        VideoAsset imported = captor.getAllValues().get(0);
        assertEquals("vid-new", imported.getTencentFileId());
        assertEquals(ResourceScopeType.SYSTEM, imported.getScopeType());
        assertEquals(VideoCloudPublishStatus.PUBLISHED, imported.getCloudPublishStatus());
        assertTrue(imported.getPublishedAt() != null);
        assertEquals(VideoStatus.READY, imported.getStatus());
        assertEquals("云端新标题", existing.getTitle());
        assertEquals(VideoCloudPublishStatus.PUBLISHED, existing.getCloudPublishStatus());
    }

    @Test
    void syncDefaultCloudSpaceShouldKeepPublishedVideoReadyWhenCloudReportsProcessing() {
        AppUser actor = admin();
        VideoStorageConfig config = defaultConfig();
        config.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);
        defaultStorageConfig = config;
        VideoAsset existing = video();
        existing.setTencentFileId("vid-existing");
        existing.setScopeType(ResourceScopeType.SYSTEM);
        existing.setCreatedBy(actor.getId());
        existing.setOwnerUserId(actor.getId());
        existing.setCloudPublishStatus(VideoCloudPublishStatus.PUBLISHED);
        existing.setPublishedAt(java.time.LocalDateTime.now());

        authenticate(actor);
        when(volcengineStorageGateway.listMedia(config, 0, 100)).thenReturn(List.of(
                new VideoCloudMediaItem(
                        "vid-existing",
                        "Cloud title",
                        null,
                        "cloud-existing.mp4",
                        "video/mp4",
                        4096L,
                        "https://volc.example.com/cloud-existing.mp4",
                        null,
                        120L,
                        false,
                        false
                )
        ));
        when(videoAssetRepository.findByTencentFileId("vid-existing")).thenReturn(Optional.of(existing));
        when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        videoAssetService.syncDefaultCloudSpace();

        assertEquals(VideoStatus.READY, existing.getStatus());
        assertEquals(VideoCloudPublishStatus.PUBLISHED, existing.getCloudPublishStatus());
        assertTrue(existing.getPublishedAt() != null);
    }

    @Test
    void syncDefaultCloudSpaceShouldRejectTeacher() {
        authenticate(teacher());

        assertThrows(AccessDeniedException.class, () -> videoAssetService.syncDefaultCloudSpace());
    }

    @Test
    void getAccessShouldRejectProcessingVideo() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        asset.setStatus(VideoStatus.PROCESSING);
        asset.setMediaUrl(null);

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        assertThrows(BadRequestException.class, () -> videoAssetService.getAccess(asset.getId()));
    }

    @Test
    void getAccessShouldReturnPreviewUrlForReadyVideo() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        asset.setCloudPublishStatus(VideoCloudPublishStatus.PUBLISHED);
        VideoStorageConfig config = defaultConfig();
        resolvedStorageConfig = config;

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(volcengineStorageGateway.describeMedia(config, "file-123")).thenReturn(
                new VideoMediaInfo(
                        "file-123",
                        "https://vod.example.com/video.mp4",
                        "https://vod.example.com/cover.jpg",
                        120L,
                        true,
                        true,
                        true,
                        null
                )
        );

        VideoAccessResponse response = videoAssetService.getAccess(asset.getId());

        assertEquals(VideoAccessMode.PREVIEW, response.getMode());
        assertEquals("https://vod.example.com/video.mp4", response.getUrl());
    }

    @Test
    void getAccessShouldRefreshSignedPlaybackUrlBeforePreview() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        asset.setMediaUrl("https://vod.example.com/expired.mp4?auth_key=expired");
        asset.setCloudPublishStatus(VideoCloudPublishStatus.PUBLISHED);
        VideoStorageConfig config = defaultConfig();
        resolvedStorageConfig = config;

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(volcengineStorageGateway.describeMedia(config, "file-123")).thenReturn(
                new VideoMediaInfo(
                        "file-123",
                        "https://vod.example.com/fresh.mp4?auth_key=fresh",
                        "https://vod.example.com/fresh-cover.jpg",
                        120L,
                        true,
                        true,
                        true,
                        null
                )
        );

        VideoAccessResponse response = videoAssetService.getAccess(asset.getId());

        assertEquals(VideoAccessMode.PREVIEW, response.getMode());
        assertEquals("https://vod.example.com/fresh.mp4?auth_key=fresh", response.getUrl());
        assertEquals("https://vod.example.com/fresh-cover.jpg", response.getCoverUrl());
    }

    @Test
    void listVisibleVideosPageShouldPreserveEnrichedFields() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        VideoStorageConfig config = defaultConfig();
        resolvedStorageConfig = config;

        authenticate(actor);
        when(videoAssetRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(asset)));
        when(appUserRepository.findAllById(any())).thenReturn(List.of(actor));
        when(videoStorageConfigRepository.findAllById(any())).thenReturn(List.of(config));

        Page<VideoResponse> page = videoAssetService.listVisibleVideosPage(1, 10, null, null, null, null);

        assertEquals(1, page.getTotalElements());
        assertEquals("老师甲", page.getContent().get(0).getCreatedByDisplayName());
        assertEquals("火山云点播", page.getContent().get(0).getStorageConfigName());
    }

    @Test
    void listVisibleVideosPageShouldAcceptCloudPublishStatusFilter() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        asset.setCloudPublishStatus(VideoCloudPublishStatus.PUBLISHED);
        VideoStorageConfig config = defaultConfig();

        authenticate(actor);
        when(videoAssetRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(asset)));
        when(appUserRepository.findAllById(any())).thenReturn(List.of(actor));
        when(videoStorageConfigRepository.findAllById(any())).thenReturn(List.of(config));

        Page<VideoResponse> page = videoAssetService.listVisibleVideosPage(
                1,
                10,
                null,
                null,
                VideoCloudPublishStatus.PUBLISHED,
                null
        );

        assertEquals(VideoCloudPublishStatus.PUBLISHED, page.getContent().get(0).getCloudPublishStatus());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listVisibleVideosPageShouldNotRequireMediaUrlForPublishedSystemVideo() {
        AppUser actor = teacher();
        ArgumentCaptor<org.springframework.data.jpa.domain.Specification<VideoAsset>> specificationCaptor =
                ArgumentCaptor.forClass(org.springframework.data.jpa.domain.Specification.class);

        authenticate(actor);
        when(videoAssetRepository.findAll(
                specificationCaptor.capture(),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        videoAssetService.listVisibleVideosPage(
                1,
                10,
                null,
                null,
                VideoCloudPublishStatus.PUBLISHED,
                ResourceScopeType.SYSTEM
        );

        jakarta.persistence.criteria.Root<VideoAsset> root = mock(jakarta.persistence.criteria.Root.class);
        jakarta.persistence.criteria.Path<Object> property = mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder = mock(jakarta.persistence.criteria.CriteriaBuilder.class);
        doReturn(property).when(root).get(anyString());

        specificationCaptor.getValue().toPredicate(
                root,
                mock(jakarta.persistence.criteria.CriteriaQuery.class),
                criteriaBuilder
        );

        verify(criteriaBuilder, never()).isNotNull(property);
    }

    private AppUser teacher() {
        AppUser actor = new AppUser();
        actor.setId(7L);
        actor.setUsername("teacher");
        actor.setPasswordHash("hashed");
        actor.setRole(UserRole.TEACHER);
        actor.setDisplayName("老师甲");
        actor.setStatus(UserStatus.ACTIVE);
        return actor;
    }

    private AppUser admin() {
        AppUser actor = new AppUser();
        actor.setId(1L);
        actor.setUsername("admin");
        actor.setPasswordHash("hashed");
        actor.setRole(UserRole.ADMIN);
        actor.setDisplayName("管理员");
        actor.setStatus(UserStatus.ACTIVE);
        return actor;
    }

    private AppUser student() {
        AppUser actor = new AppUser();
        actor.setId(9L);
        actor.setUsername("student");
        actor.setPasswordHash("hashed");
        actor.setRole(UserRole.STUDENT);
        actor.setDisplayName("学生甲");
        actor.setStatus(UserStatus.ACTIVE);
        return actor;
    }

    private VideoStorageConfig defaultConfig() {
        VideoStorageConfig config = new VideoStorageConfig();
        config.setId(3L);
        config.setConfigName("火山云点播");
        config.setProviderType(VideoStorageProviderType.VOLCENGINE_VOD);
        return config;
    }

    private VideoAsset video() {
        VideoAsset asset = new VideoAsset();
        asset.setId(8L);
        asset.setTitle("教学视频");
        asset.setOriginalFileName("video.mp4");
        asset.setFileSize(1024L);
        asset.setTencentFileId("file-123");
        asset.setMediaUrl("https://vod.example.com/video.mp4");
        asset.setCoverUrl("https://vod.example.com/cover.jpg");
        asset.setStatus(VideoStatus.READY);
        asset.setCreatedBy(7L);
        asset.setOwnerUserId(7L);
        asset.setScopeType(ResourceScopeType.TEACHER);
        asset.setStorageConfigId(3L);
        return asset;
    }

    private MockMultipartFile videoFile() {
        return new MockMultipartFile("file", "lesson.mp4", "video/mp4", "video-bytes".getBytes());
    }

    private void stubUploadWithProcessingCloudMetadata(VideoStorageConfig config, AppUser actor) {
        when(volcengineStorageGateway.upload(any(), any(), any(), any(), any())).thenReturn(
                new VideoUploadResult(
                        "file-123",
                        "https://vod.example.com/lesson.mp4",
                        "https://vod.example.com/cover.jpg",
                        false
                )
        );
        when(volcengineStorageGateway.describeMedia(config, "file-123")).thenReturn(
                new VideoMediaInfo(
                        "file-123",
                        "https://vod.example.com/lesson.mp4",
                        "https://vod.example.com/cover.jpg",
                        120L,
                        false,
                        false,
                        false,
                        null
                )
        );
        when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> {
            VideoAsset asset = invocation.getArgument(0);
            asset.setId(55L);
            return asset;
        });
        when(appUserRepository.findAllById(any())).thenReturn(List.of(actor));
        when(videoStorageConfigRepository.findAllById(any())).thenReturn(List.of(config));
    }

    private void authenticate(AppUser actor) {
        when(appUserRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        AuthenticatedUser principal = AuthenticatedUser.from(actor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,
                actor.getPasswordHash(),
                principal.getAuthorities()
        ));
    }
}
