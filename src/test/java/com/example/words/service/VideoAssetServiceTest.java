package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.VideoAccessResponse;
import com.example.words.dto.VideoResponse;
import com.example.words.dto.StudentVideoResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.ResourceScopeType;
import com.example.words.model.UserRole;
import com.example.words.model.UserStatus;
import com.example.words.model.VideoAccessMode;
import com.example.words.model.VideoAsset;
import com.example.words.model.VideoPublishStatus;
import com.example.words.model.VideoStatus;
import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageProviderType;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.VideoAssetRepository;
import com.example.words.repository.VideoStorageConfigRepository;
import com.example.words.security.AuthenticatedUser;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;
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
        when(tencentStorageGateway.upload(any(), any(), any(), any(), any())).thenReturn(
                new VideoUploadResult(
                        "file-123",
                        "https://vod.example.com/lesson.mp4",
                        "https://vod.example.com/cover.jpg",
                        false
                )
        );
        when(tencentStorageGateway.describeMedia(config, "file-123")).thenReturn(
                new VideoMediaInfo(
                        "file-123",
                        "https://vod.example.com/lesson.mp4",
                        "https://vod.example.com/cover.jpg",
                        120L,
                        true,
                        true
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
        assertEquals(VideoPublishStatus.UNPUBLISHED, response.getPublishStatus());
        assertTrue(response.isCanManage());
        verify(tencentStorageGateway).upload(any(), any(), any(), any(), any());
    }

    @Test
    void publishShouldMarkReadyVideoAsPublished() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        VideoStorageConfig config = defaultConfig();

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findAllById(any())).thenReturn(List.of(actor));
        when(videoStorageConfigRepository.findAllById(any())).thenReturn(List.of(config));

        VideoResponse response = videoAssetService.publish(asset.getId());

        assertEquals(VideoPublishStatus.PUBLISHED, response.getPublishStatus());
        assertTrue(asset.getPublishedAt() != null);
    }

    @Test
    void publishShouldRejectVideoWithoutPlayableUrl() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        asset.setMediaUrl(null);

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        assertThrows(BadRequestException.class, () -> videoAssetService.publish(asset.getId()));
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

        authenticate(actor);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        VideoAccessResponse response = videoAssetService.getAccess(asset.getId());

        assertEquals(VideoAccessMode.PREVIEW, response.getMode());
        assertEquals("https://vod.example.com/video.mp4", response.getUrl());
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
        assertEquals("腾讯云广州", page.getContent().get(0).getStorageConfigName());
    }

    @Test
    void listVisibleVideosPageShouldAcceptPublishStatusFilter() {
        AppUser actor = teacher();
        VideoAsset asset = video();
        asset.setPublishStatus(VideoPublishStatus.PUBLISHED);
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
                VideoPublishStatus.PUBLISHED,
                null
        );

        assertEquals(VideoPublishStatus.PUBLISHED, page.getContent().get(0).getPublishStatus());
    }

    @Test
    void listStudentVideosPageShouldReturnPublishedSystemVideos() {
        AppUser student = student();
        AppUser teacher = teacher();
        VideoAsset asset = video();
        asset.setScopeType(ResourceScopeType.SYSTEM);
        asset.setPublishStatus(VideoPublishStatus.PUBLISHED);

        authenticate(student);
        when(videoAssetRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(asset)));
        when(appUserRepository.findAllById(any())).thenReturn(List.of(teacher));

        Page<StudentVideoResponse> page = videoAssetService.listStudentVideosPage(1, 10, null);

        assertEquals(1, page.getTotalElements());
        assertEquals("教学视频", page.getContent().get(0).getTitle());
        assertEquals("老师甲", page.getContent().get(0).getCreatedByDisplayName());
    }

    @Test
    void getStudentPlaybackShouldReturnPlayUrlForPublishedSystemVideo() {
        AppUser student = student();
        VideoAsset asset = video();
        asset.setScopeType(ResourceScopeType.SYSTEM);
        asset.setPublishStatus(VideoPublishStatus.PUBLISHED);

        authenticate(student);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        VideoAccessResponse response = videoAssetService.getStudentPlayback(asset.getId());

        assertEquals(VideoAccessMode.PLAY, response.getMode());
        assertEquals("https://vod.example.com/video.mp4", response.getUrl());
    }

    @Test
    void getStudentPlaybackShouldRejectUnpublishedVideo() {
        AppUser student = student();
        VideoAsset asset = video();
        asset.setScopeType(ResourceScopeType.SYSTEM);

        authenticate(student);
        when(videoAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        assertThrows(ResourceNotFoundException.class, () -> videoAssetService.getStudentPlayback(asset.getId()));
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
        config.setConfigName("腾讯云广州");
        config.setProviderType(VideoStorageProviderType.TENCENT_VOD);
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
