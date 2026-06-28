package com.example.words.service.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageProviderType;
import com.example.words.service.VideoStorageConfigCryptoService;
import com.example.words.service.VideoStorageConfigService;
import com.volcengine.service.vod.IVodService;
import com.volcengine.service.vod.impl.VodServiceImpl;
import com.volcengine.service.vod.model.business.ValuePair;
import com.volcengine.service.vod.model.business.VodPlayInfo;
import com.volcengine.service.vod.model.business.VodURLSet;
import com.volcengine.service.vod.model.business.VodUrlUploadURLSet;
import com.volcengine.service.vod.model.request.VodGetPlayInfoRequest;
import com.volcengine.service.vod.model.request.VodQueryUploadTaskInfoRequest;
import com.volcengine.service.vod.model.request.VodUpdateMediaPublishStatusRequest;
import com.volcengine.service.vod.model.request.VodUrlUploadRequest;
import com.volcengine.service.vod.model.response.VodGetPlayInfoResponse;
import com.volcengine.service.vod.model.response.VodQueryUploadTaskInfoResponse;
import com.volcengine.service.vod.model.response.VodUpdateMediaPublishStatusResponse;
import com.volcengine.service.vod.model.response.VodUrlUploadResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "ai.config.encryption-key=${AI_CONFIG_ENCRYPTION_KEY:MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=}",
        "video.storage.config.encryption-key=${VIDEO_STORAGE_CONFIG_ENCRYPTION_KEY:"
                + "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=}"
})
@Tag("integration")
@EnabledIfSystemProperty(named = "volcengine.vod.liveTest", matches = "true")
class VolcengineVodLiveUploadTest {

    private static final Path DEFAULT_UPLOAD_FILE = Path.of("/Users/wyn/Downloads/liuxiaoyan.mp4");
    private static final String DEFAULT_URL_UPLOAD_SOURCE =
            "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4";
    private static final int PUBLISH_CHECK_MAX_ATTEMPTS = 20;
    private static final long PUBLISH_CHECK_INTERVAL_MILLIS = 3000L;
    private static final int URL_UPLOAD_CHECK_MAX_ATTEMPTS = 48;
    private static final long URL_UPLOAD_CHECK_INTERVAL_MILLIS = 5000L;

    @Autowired
    private VideoStorageConfigService videoStorageConfigService;

    @Autowired
    private VideoStorageConfigCryptoService cryptoService;

    @Autowired
    private VolcengineVodStorageGateway volcengineVodStorageGateway;

    @Test
    @EnabledIfSystemProperty(named = "volcengine.vod.livePublishTest", matches = "true")
    void publishExistingVolcengineMediaShouldSucceedWithCurrentDefaultConfig() throws Exception {
        VideoStorageConfig config = defaultVolcengineConfig();
        String mediaId = existingMediaId();

        publishMediaWithOfficialDemoCall(config, mediaId);

        VideoCloudMediaItem cloudMediaItem = waitForCloudPublished(config, mediaId);
        assertTrue(
                cloudMediaItem.cloudPublished(),
                "Volcengine VOD media must be Published after publish request: " + mediaId
        );

        System.out.println("Volcengine VOD live publish media Vid: " + mediaId);
        System.out.println("Volcengine VOD live publish status: Published");
    }

    @Test
    @EnabledIfSystemProperty(named = "volcengine.vod.livePlayInfoTest", matches = "true")
    void printExistingVolcengineMediaPlayInfoWithCurrentDefaultConfig() throws Exception {
        VideoStorageConfig config = defaultVolcengineConfig();
        String mediaId = existingMediaId();

        IVodService vodService = VodServiceImpl.getInstance();
        vodService.setAccessKey(cryptoService.decrypt(config.getSecretIdEncrypted()).trim());
        vodService.setSecretKey(cryptoService.decrypt(config.getSecretKeyEncrypted()).trim());

        VodGetPlayInfoRequest request = VodGetPlayInfoRequest.newBuilder()
                .setVid(mediaId)
                .setSsl("1")
                .setGetAll(true)
                .build();
        VodGetPlayInfoResponse response;
        try {
            response = vodService.getPlayInfo(request);
        } catch (Exception ex) {
            String message = rootMessage(ex);
            System.out.println("Volcengine VOD play info unavailable: " + message);
            if (message.contains("ResourceNotFound.NoAvailableDomain")) {
                return;
            }
            throw ex;
        }

        System.out.println(response);
        if (response.getResponseMetadata().hasError()) {
            throw new AssertionError("查询播放信息失败：" + response.getResponseMetadata().getError());
        }
        if (!response.hasResult()) {
            throw new AssertionError("查询播放信息没有返回 Result，Vid: " + mediaId);
        }

        System.out.println("Volcengine VOD play info Vid: " + mediaId);
        System.out.println("Volcengine VOD play info status: " + response.getResult().getStatus());
        System.out.println("Volcengine VOD play info totalCount: " + response.getResult().getTotalCount());
        System.out.println("Volcengine VOD play info listCount: " + response.getResult().getPlayInfoListCount());
        for (VodPlayInfo playInfo : response.getResult().getPlayInfoListList()) {
            System.out.println("Volcengine VOD play item definition=" + playInfo.getDefinition()
                    + ", format=" + playInfo.getFormat()
                    + ", mainPlayUrl=" + playInfo.getMainPlayUrl()
                    + ", backupPlayUrl=" + playInfo.getBackupPlayUrl());
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    @Test
    @EnabledIfSystemProperty(named = "volcengine.vod.liveUploadTest", matches = "true")
    void uploadLiuXiaoyanMp4ShouldPublishWithCurrentDefaultVolcengineConfig() {
        VideoStorageConfig config = defaultVolcengineConfig();

        Path uploadFile = resolveUploadFile();
        String originalFileName = uploadFile.getFileName().toString();
        String title = "liu-xiaoyan-live-upload-" + System.currentTimeMillis();
        VideoUploadResult result = volcengineVodStorageGateway.upload(
                config,
                uploadFile,
                originalFileName,
                title,
                "Codex live upload smoke test"
        );
        String mediaId = result.mediaId();

        assertFalse(mediaId == null || mediaId.isBlank(), "Volcengine VOD should return a Vid");

        VideoMediaInfo mediaInfo = volcengineVodStorageGateway.describeMedia(config, mediaId);
        assertEquals(mediaId, mediaInfo.mediaId());

        VideoCloudMediaItem cloudMediaItem = waitForCloudPublished(config, mediaId);
        assertTrue(
                cloudMediaItem.cloudPublished(),
                "Volcengine VOD media must be Published after upload: " + mediaId
        );

        System.out.println("Volcengine VOD live upload kept media title: " + title);
        System.out.println("Volcengine VOD live upload kept media Vid: " + mediaId);
        System.out.println("Volcengine VOD live upload publish status: Published");
    }

    @Test
    @EnabledIfSystemProperty(named = "volcengine.vod.liveUrlUploadTest", matches = "true")
    void uploadMediaByUrlShouldPublishWithCurrentDefaultVolcengineConfig() throws Exception {
        VideoStorageConfig config = defaultVolcengineConfig();
        String sourceUrl = urlUploadSourceUrl();
        String title = "codex-url-upload-" + System.currentTimeMillis();

        String jobId = uploadMediaByUrlWithOfficialDemoCall(config, sourceUrl, title);
        VodURLSet urlUploadResult = waitForUrlUploadFinished(config, jobId);
        String mediaId = urlUploadResult.getVid();

        assertFalse(mediaId == null || mediaId.isBlank(), "Volcengine URL upload task should return a Vid");

        publishMediaWithOfficialDemoCall(config, mediaId);

        VideoCloudMediaItem cloudMediaItem = waitForCloudPublished(config, mediaId);
        assertTrue(
                cloudMediaItem.cloudPublished(),
                "Volcengine VOD URL-uploaded media must be Published after publish request: " + mediaId
        );

        System.out.println("Volcengine VOD live URL upload source: " + sourceUrl);
        System.out.println("Volcengine VOD live URL upload jobId: " + jobId);
        System.out.println("Volcengine VOD live URL upload media Vid: " + mediaId);
        System.out.println("Volcengine VOD live URL upload publish status: Published");
    }

    private VideoStorageConfig defaultVolcengineConfig() {
        VideoStorageConfig config = videoStorageConfigService.getDefaultEnabledConfig();
        assertEquals(
                VideoStorageProviderType.VOLCENGINE_VOD,
                config.getProviderType(),
                "Default enabled video storage config must be VOLCENGINE_VOD"
        );
        return config;
    }

    private String existingMediaId() {
        String mediaId = System.getProperty("volcengine.vod.mediaId");
        if (mediaId == null || mediaId.isBlank()) {
            throw new IllegalStateException("Set -Dvolcengine.vod.mediaId=<Vid> before running live publish test");
        }
        return mediaId.trim();
    }

    private void publishMediaWithOfficialDemoCall(VideoStorageConfig config, String mediaId) throws Exception {
        IVodService vodService = VodServiceImpl.getInstance();
        vodService.setAccessKey(cryptoService.decrypt(config.getSecretIdEncrypted()).trim());
        vodService.setSecretKey(cryptoService.decrypt(config.getSecretKeyEncrypted()).trim());

        VodUpdateMediaPublishStatusRequest.Builder request = VodUpdateMediaPublishStatusRequest.newBuilder();
        request.setVid(mediaId);
        request.setStatus("Published");

        VodUpdateMediaPublishStatusResponse response = vodService.updateMediaPublishStatus(request.build());
        System.out.println(response);
        if (response.getResponseMetadata().hasError()) {
            throw new AssertionError("发布失败：" + response.getResponseMetadata().getError());
        }
        System.out.println("发布成功");
    }

    private String uploadMediaByUrlWithOfficialDemoCall(
            VideoStorageConfig config,
            String sourceUrl,
            String title
    ) throws Exception {
        IVodService vodService = VodServiceImpl.getInstance();
        vodService.setAccessKey(cryptoService.decrypt(config.getSecretIdEncrypted()).trim());
        vodService.setSecretKey(cryptoService.decrypt(config.getSecretKeyEncrypted()).trim());

        VodUrlUploadRequest.Builder request = VodUrlUploadRequest.newBuilder();
        request.setSpaceName(config.getSpaceName().trim());

        VodUrlUploadURLSet.Builder urlSet = VodUrlUploadURLSet.newBuilder();
        urlSet.setSourceUrl(sourceUrl);
        urlSet.setStorageClass(1);
        urlSet.setFileExtension(".mp4");
        urlSet.setTitle(title);
        urlSet.setFileName(title + ".mp4");
        urlSet.setCallbackArgs("codex-live-url-upload-test");
        request.addURLSets(urlSet);

        VodUrlUploadResponse response = vodService.uploadMediaByUrl(request.build());
        System.out.println(response);
        if (response.getResponseMetadata().hasError()) {
            throw new AssertionError("URL 上传失败：" + response.getResponseMetadata().getError());
        }
        List<ValuePair> data = response.getResult().getDataList();
        if (data.isEmpty() || data.get(0).getJobId().isBlank()) {
            throw new AssertionError("URL 上传接口没有返回 JobId: " + response);
        }
        System.out.println("URL 上传任务创建成功，JobId: " + data.get(0).getJobId());
        return data.get(0).getJobId();
    }

    private VodURLSet waitForUrlUploadFinished(VideoStorageConfig config, String jobId) throws Exception {
        IVodService vodService = VodServiceImpl.getInstance();
        vodService.setAccessKey(cryptoService.decrypt(config.getSecretIdEncrypted()).trim());
        vodService.setSecretKey(cryptoService.decrypt(config.getSecretKeyEncrypted()).trim());

        VodURLSet latest = null;
        for (int attempt = 0; attempt < URL_UPLOAD_CHECK_MAX_ATTEMPTS; attempt++) {
            VodQueryUploadTaskInfoRequest.Builder request = VodQueryUploadTaskInfoRequest.newBuilder();
            request.setJobIds(jobId);

            VodQueryUploadTaskInfoResponse response = vodService.queryUploadTaskInfo(request.build());
            System.out.println(response);
            if (response.getResponseMetadata().hasError()) {
                throw new AssertionError("查询 URL 上传任务失败：" + response.getResponseMetadata().getError());
            }
            if (response.getResult().getData().getNotExistJobIdsList().contains(jobId)) {
                throw new AssertionError("URL 上传任务不存在，JobId: " + jobId);
            }
            Optional<VodURLSet> match = response.getResult().getData().getMediaInfoListList().stream()
                    .filter(item -> jobId.equals(item.getJobId()))
                    .findFirst();
            if (match.isPresent()) {
                latest = match.get();
                if (isFailedUrlUploadState(latest.getState())) {
                    throw new AssertionError("URL 上传任务失败，JobId: " + jobId + ", state: " + latest.getState());
                }
                if (!latest.getVid().isBlank()) {
                    return latest;
                }
            }
            sleepQuietly(URL_UPLOAD_CHECK_INTERVAL_MILLIS);
        }
        throw new AssertionError("URL 上传任务未在限定时间内返回 Vid，JobId: " + jobId
                + ", latest state: " + (latest == null ? "<none>" : latest.getState()));
    }

    private boolean isFailedUrlUploadState(String state) {
        String normalized = state == null ? "" : state.trim().toLowerCase();
        return normalized.contains("fail") || normalized.contains("error");
    }

    private String urlUploadSourceUrl() {
        String sourceUrl = System.getProperty("volcengine.vod.sourceUrl");
        return sourceUrl == null || sourceUrl.isBlank()
                ? DEFAULT_URL_UPLOAD_SOURCE
                : sourceUrl.trim();
    }

    private Path resolveUploadFile() {
        String uploadFile = System.getProperty("volcengine.vod.uploadFile");
        Path path = uploadFile == null || uploadFile.isBlank()
                ? DEFAULT_UPLOAD_FILE
                : Path.of(uploadFile);
        path = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Volcengine live upload test file does not exist: " + path);
        }
        return path;
    }

    private VideoCloudMediaItem waitForCloudPublished(VideoStorageConfig config, String mediaId) {
        VideoCloudMediaItem latest = null;
        for (int attempt = 0; attempt < PUBLISH_CHECK_MAX_ATTEMPTS; attempt++) {
            Optional<VideoCloudMediaItem> match = findCloudMedia(config, mediaId);
            if (match.isPresent()) {
                latest = match.get();
                if (latest.cloudPublished()) {
                    return latest;
                }
            }
            sleepQuietly(PUBLISH_CHECK_INTERVAL_MILLIS);
        }
        if (latest == null) {
            throw new AssertionError("Uploaded Volcengine VOD media was not found in cloud media list: " + mediaId);
        }
        return latest;
    }

    private Optional<VideoCloudMediaItem> findCloudMedia(VideoStorageConfig config, String mediaId) {
        int offset = 0;
        int pageSize = 50;
        while (offset < 500) {
            List<VideoCloudMediaItem> mediaItems = volcengineVodStorageGateway.listMedia(config, offset, pageSize);
            Optional<VideoCloudMediaItem> match = mediaItems.stream()
                    .filter(item -> mediaId.equals(item.mediaId()))
                    .findFirst();
            if (match.isPresent() || mediaItems.size() < pageSize) {
                return match;
            }
            offset += mediaItems.size();
        }
        return Optional.empty();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Volcengine VOD status", ex);
        }
    }
}
