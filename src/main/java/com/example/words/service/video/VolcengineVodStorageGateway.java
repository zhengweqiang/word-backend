package com.example.words.service.video;

import com.example.words.exception.BadGatewayException;
import com.example.words.exception.BadRequestException;
import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageProviderType;
import com.example.words.service.VideoStorageConfigCryptoService;
import com.volcengine.service.vod.IVodService;
import com.volcengine.service.vod.impl.VodServiceImpl;
import com.volcengine.service.vod.model.business.VodMediaBasicInfo;
import com.volcengine.service.vod.model.business.VodMediaInfo;
import com.volcengine.service.vod.model.business.VodPlayInfo;
import com.volcengine.service.vod.model.business.VodPlayInfoModel;
import com.volcengine.service.vod.model.business.VodSourceInfo;
import com.volcengine.service.vod.model.request.VodDeleteMediaRequest;
import com.volcengine.service.vod.model.request.VodGetMediaListRequest;
import com.volcengine.service.vod.model.request.VodGetMediaInfosRequest;
import com.volcengine.service.vod.model.request.VodGetPlayInfoRequest;
import com.volcengine.service.vod.model.request.VodGetSpaceDetailRequest;
import com.volcengine.service.vod.model.request.VodUpdateMediaPublishStatusRequest;
import com.volcengine.service.vod.model.request.VodUploadMediaRequest;
import com.volcengine.service.vod.model.response.VodCommitUploadInfoResponse;
import com.volcengine.service.vod.model.response.VodGetMediaListResponse;
import com.volcengine.service.vod.model.response.VodGetMediaInfosResponse;
import com.volcengine.service.vod.model.response.VodGetPlayInfoResponse;
import com.volcengine.service.vod.model.response.VodUpdateMediaPublishStatusResponse;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VolcengineVodStorageGateway implements VideoStorageGateway {

    private final VideoStorageConfigCryptoService cryptoService;
    private final VodServiceFactory vodServiceFactory;

    @Autowired
    public VolcengineVodStorageGateway(VideoStorageConfigCryptoService cryptoService) {
        this(cryptoService, VodServiceImpl::getInstance);
    }

    VolcengineVodStorageGateway(
            VideoStorageConfigCryptoService cryptoService,
            VodServiceFactory vodServiceFactory) {
        this.cryptoService = cryptoService;
        this.vodServiceFactory = vodServiceFactory;
    }

    @Override
    public VideoStorageProviderType providerType() {
        return VideoStorageProviderType.VOLCENGINE_VOD;
    }

    @Override
    public VideoUploadResult upload(
            VideoStorageConfig config,
            Path filePath,
            String originalFileName,
            String title,
            String description) {
        try {
            requireConfigReady(config);
            VodUploadMediaRequest.Builder requestBuilder = VodUploadMediaRequest.newBuilder()
                    .setSpaceName(config.getSpaceName().trim())
                    .setFilePath(filePath.toString())
                    .setFileName(resolveFileName(originalFileName, title))
                    .setFileExtension(resolveFileExtension(originalFileName));
            if (config.getProcedureName() != null && !config.getProcedureName().isBlank()) {
                requestBuilder.setFunctions(config.getProcedureName().trim());
            }

            VodCommitUploadInfoResponse response = buildVodService(config).uploadMedia(requestBuilder.build(), null);
            if (!response.hasResult() || !response.getResult().hasData()) {
                throw new BadGatewayException("Volcengine VOD upload failed: missing upload result");
            }

            String vid = response.getResult().getData().getVid();
            if (vid == null || vid.isBlank()) {
                throw new BadGatewayException("Volcengine VOD upload failed: missing Vid");
            }
            publishMedia(config, vid);

            VideoMediaInfo mediaInfo = describeMedia(config, vid);
            return new VideoUploadResult(vid, mediaInfo.mediaUrl(), mediaInfo.coverUrl(), false);
        } catch (BadRequestException | BadGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadGatewayException("Volcengine VOD upload failed: " + rootMessage(ex));
        }
    }

    @Override
    public VideoMediaInfo describeMedia(VideoStorageConfig config, String cloudMediaId) {
        VodMediaInfo mediaInfo = null;
        boolean cloudPublished = false;
        try {
            requireConfigReady(config);
            IVodService service = buildVodService(config);

            VodGetMediaInfosRequest mediaInfoRequest = VodGetMediaInfosRequest.newBuilder()
                    .setVids(cloudMediaId)
                    .build();
            VodGetMediaInfosResponse mediaInfoResponse = service.getMediaInfos(mediaInfoRequest);
            mediaInfo = mediaInfoResponse.hasResult() && mediaInfoResponse.getResult().getMediaInfoListCount() > 0
                    ? mediaInfoResponse.getResult().getMediaInfoList(0)
                    : null;
            if (mediaInfo == null) {
                return new VideoMediaInfo(
                        cloudMediaId,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false,
                        true,
                        "云端视频媒资不存在或已被删除"
                );
            }
            cloudPublished = cloudPublished(mediaInfo);

            VodGetPlayInfoRequest playInfoRequest = VodGetPlayInfoRequest.newBuilder()
                    .setVid(cloudMediaId)
                    .setSsl("1")
                    .setGetAll(true)
                    .build();
            VodGetPlayInfoResponse playInfoResponse = service.getPlayInfo(playInfoRequest);
            VodPlayInfoModel playInfoModel = playInfoResponse.hasResult() ? playInfoResponse.getResult() : null;

            String mediaUrl = resolvePlayUrl(playInfoModel);
            String coverUrl = resolveCoverUrl(playInfoModel, mediaInfo);
            Long durationSeconds = resolveDurationSeconds(playInfoModel, mediaInfo);
            boolean ready = mediaUrl != null && !mediaUrl.isBlank();
            return new VideoMediaInfo(cloudMediaId, mediaUrl, coverUrl, durationSeconds, ready, ready, cloudPublished, null);
        } catch (BadRequestException | BadGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            String message = rootMessage(ex);
            if (isNoAvailableDomainError(message)) {
                return new VideoMediaInfo(
                        cloudMediaId,
                        null,
                        resolveCoverUrl(null, mediaInfo),
                        resolveDurationSeconds(null, mediaInfo),
                        false,
                        false,
                        cloudPublished,
                        "火山云点播未配置有效播放域名，无法获取播放地址"
                );
            }
            throw new BadGatewayException("Volcengine VOD media query failed: " + message);
        }
    }

    @Override
    public List<VideoCloudMediaItem> listMedia(VideoStorageConfig config, int offset, int pageSize) {
        try {
            requireConfigReady(config);
            VodGetMediaListRequest request = VodGetMediaListRequest.newBuilder()
                    .setSpaceName(config.getSpaceName().trim())
                    .setOffset(String.valueOf(Math.max(offset, 0)))
                    .setPageSize(String.valueOf(Math.max(pageSize, 1)))
                    .setOrder("Desc")
                    .build();
            VodGetMediaListResponse response = buildVodService(config).getMediaList(request);
            if (response.getResponseMetadata().hasError()) {
                throw new BadGatewayException(
                        "Volcengine VOD media list failed: " + response.getResponseMetadata().getError().getMessage()
                );
            }
            if (!response.hasResult() || response.getResult().getMediaInfoListCount() == 0) {
                return List.of();
            }
            return response.getResult().getMediaInfoListList().stream()
                    .map(item -> toCloudMediaItem(config, item))
                    .toList();
        } catch (BadRequestException | BadGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadGatewayException("Volcengine VOD media list failed: " + rootMessage(ex));
        }
    }

    @Override
    public void publishMedia(VideoStorageConfig config, String cloudMediaId) {
        updateMediaPublishStatus(config, cloudMediaId, "Published", "publish");
    }

    @Override
    public void unpublishMedia(VideoStorageConfig config, String cloudMediaId) {
        updateMediaPublishStatus(config, cloudMediaId, "Unpublished", "unpublish");
    }

    private void updateMediaPublishStatus(
            VideoStorageConfig config,
            String cloudMediaId,
            String cloudStatus,
            String operation) {
        try {
            requireConfigReady(config);
            VodUpdateMediaPublishStatusRequest request = VodUpdateMediaPublishStatusRequest.newBuilder()
                    .setVid(cloudMediaId)
                    .setStatus(cloudStatus)
                    .build();
            VodUpdateMediaPublishStatusResponse response = buildVodService(config).updateMediaPublishStatus(request);
            if (response.hasResponseMetadata() && response.getResponseMetadata().hasError()) {
                throw new BadGatewayException(
                        "Volcengine VOD " + operation + " failed: "
                                + response.getResponseMetadata().getError().getMessage()
                );
            }
        } catch (BadRequestException | BadGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadGatewayException("Volcengine VOD " + operation + " failed: " + rootMessage(ex));
        }
    }

    @Override
    public void deleteMedia(VideoStorageConfig config, String cloudMediaId) {
        try {
            requireConfigReady(config);
            VodDeleteMediaRequest request = VodDeleteMediaRequest.newBuilder()
                    .setVids(cloudMediaId)
                    .build();
            buildVodService(config).deleteMedia(request);
        } catch (BadRequestException | BadGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            String message = rootMessage(ex);
            if (message != null && message.contains("not exist")) {
                return;
            }
            throw new BadGatewayException("Volcengine VOD delete failed: " + message);
        }
    }

    @Override
    public void validate(VideoStorageConfig config) {
        try {
            requireConfigReady(config);
            VodGetSpaceDetailRequest request = VodGetSpaceDetailRequest.newBuilder()
                    .setSpaceName(config.getSpaceName().trim())
                    .build();
            buildVodService(config).getSpaceDetail(request);
        } catch (BadRequestException | BadGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadGatewayException(buildValidationErrorMessage(ex));
        }
    }

    private IVodService buildVodService(VideoStorageConfig config) throws Exception {
        IVodService service = vodServiceFactory.create(config.getRegion().trim());
        service.setAccessKey(cryptoService.decrypt(config.getSecretIdEncrypted()).trim());
        service.setSecretKey(cryptoService.decrypt(config.getSecretKeyEncrypted()).trim());
        service.setRegion(config.getRegion().trim());
        service.setConnectionTimeout(10_000);
        service.setSocketTimeout(30_000);
        return service;
    }

    private void requireConfigReady(VideoStorageConfig config) {
        if (config.getRegion() == null || config.getRegion().isBlank()) {
            throw new BadRequestException("region is required for Volcengine VOD");
        }
        if (config.getSpaceName() == null || config.getSpaceName().isBlank()) {
            throw new BadRequestException("spaceName is required for Volcengine VOD");
        }

        String accessKeyId = cryptoService.decrypt(config.getSecretIdEncrypted()).trim();
        String secretAccessKey = cryptoService.decrypt(config.getSecretKeyEncrypted()).trim();
        if (accessKeyId.length() < 8 || secretAccessKey.length() < 8) {
            throw new BadRequestException(
                    "Current video storage config still uses placeholder AccessKeyId/SecretAccessKey. "
                            + "Please save real Volcengine VOD credentials before testing"
            );
        }
    }

    private String resolvePlayUrl(VodPlayInfoModel playInfoModel) {
        if (playInfoModel == null || playInfoModel.getPlayInfoListCount() == 0) {
            return null;
        }
        return playInfoModel.getPlayInfoListList().stream()
                .filter(item -> item != null && playUrl(item) != null)
                .sorted(Comparator
                        .comparing((VodPlayInfo item) -> !"360p".equalsIgnoreCase(item.getDefinition()))
                        .thenComparing(item -> item.getHeight() <= 0 ? Integer.MAX_VALUE : item.getHeight())
                        .thenComparing(item -> item.getBitrate() <= 0 ? Integer.MAX_VALUE : item.getBitrate()))
                .map(this::playUrl)
                .findFirst()
                .orElse(null);
    }

    private String playUrl(VodPlayInfo item) {
        return firstNonBlank(item.getMainPlayUrl(), item.getBackupPlayUrl());
    }

    private String resolveCoverUrl(VodPlayInfoModel playInfoModel, VodMediaInfo mediaInfo) {
        if (playInfoModel != null && playInfoModel.getPosterUrl() != null && !playInfoModel.getPosterUrl().isBlank()) {
            return playInfoModel.getPosterUrl();
        }
        if (mediaInfo != null && mediaInfo.hasBasicInfo()) {
            VodMediaBasicInfo basicInfo = mediaInfo.getBasicInfo();
            if (basicInfo.getPosterUri() != null && !basicInfo.getPosterUri().isBlank()) {
                return basicInfo.getPosterUri();
            }
        }
        return null;
    }

    private Long resolveDurationSeconds(VodPlayInfoModel playInfoModel, VodMediaInfo mediaInfo) {
        if (playInfoModel != null && playInfoModel.getDuration() > 0) {
            return (long) Math.round(playInfoModel.getDuration());
        }
        if (mediaInfo != null && mediaInfo.hasSourceInfo()) {
            VodSourceInfo sourceInfo = mediaInfo.getSourceInfo();
            if (sourceInfo.getDuration() > 0) {
                return (long) Math.round(sourceInfo.getDuration());
            }
        }
        return null;
    }

    private VideoCloudMediaItem toCloudMediaItem(VideoStorageConfig config, VodMediaInfo mediaInfo) {
        VodMediaBasicInfo basicInfo = mediaInfo.hasBasicInfo() ? mediaInfo.getBasicInfo() : null;
        VodSourceInfo sourceInfo = mediaInfo.hasSourceInfo() ? mediaInfo.getSourceInfo() : null;
        String mediaId = basicInfo == null ? null : trimToNull(basicInfo.getVid());
        VideoMediaInfo playbackInfo = mediaId == null
                ? new VideoMediaInfo(null, null, null, null, false, false)
                : describeMedia(config, mediaId);
        Long durationSeconds = playbackInfo.durationSeconds() != null
                ? playbackInfo.durationSeconds()
                : durationSeconds(sourceInfo);
        String title = firstNonBlank(
                basicInfo == null ? null : basicInfo.getTitle(),
                sourceInfo == null ? null : sourceInfo.getFileName(),
                mediaId
        );
        String originalFileName = firstNonBlank(
                sourceInfo == null ? null : sourceInfo.getFileName(),
                title == null ? null : title + extensionFromFormat(sourceInfo)
        );
        String contentType = contentType(sourceInfo);
        Long fileSize = fileSize(sourceInfo, basicInfo);
        boolean cloudPublished = cloudPublished(mediaInfo);

        return new VideoCloudMediaItem(
                mediaId,
                title,
                trimToNull(basicInfo == null ? null : basicInfo.getDescription()),
                originalFileName,
                contentType,
                fileSize == null ? 0L : fileSize,
                playbackInfo.mediaUrl(),
                firstNonBlank(playbackInfo.coverUrl(), basicInfo == null ? null : basicInfo.getPosterUri()),
                durationSeconds,
                playbackInfo.ready(),
                cloudPublished
        );
    }

    private boolean cloudPublished(VodMediaInfo mediaInfo) {
        VodMediaBasicInfo basicInfo = mediaInfo != null && mediaInfo.hasBasicInfo() ? mediaInfo.getBasicInfo() : null;
        return basicInfo != null && "Published".equalsIgnoreCase(basicInfo.getPublishStatus());
    }

    private Long durationSeconds(VodSourceInfo sourceInfo) {
        if (sourceInfo == null || sourceInfo.getDuration() <= 0) {
            return null;
        }
        return (long) Math.round(sourceInfo.getDuration());
    }

    private Long fileSize(VodSourceInfo sourceInfo, VodMediaBasicInfo basicInfo) {
        if (sourceInfo != null && sourceInfo.getSize() > 0) {
            return Math.round(sourceInfo.getSize());
        }
        if (basicInfo != null && basicInfo.getHlsMediaSize() > 0) {
            return Math.round(basicInfo.getHlsMediaSize());
        }
        return null;
    }

    private String contentType(VodSourceInfo sourceInfo) {
        String format = sourceInfo == null ? null : trimToNull(sourceInfo.getFormat());
        return format == null ? null : "video/" + format.toLowerCase(java.util.Locale.ROOT);
    }

    private String extensionFromFormat(VodSourceInfo sourceInfo) {
        String format = sourceInfo == null ? null : trimToNull(sourceInfo.getFormat());
        return format == null ? ".mp4" : "." + format.toLowerCase(java.util.Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = trimToNull(first);
        return normalizedFirst != null ? normalizedFirst : trimToNull(second);
    }

    private String firstNonBlank(String first, String second, String third) {
        String resolved = firstNonBlank(first, second);
        return resolved != null ? resolved : trimToNull(third);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String resolveFileName(String originalFileName, String title) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        int extensionIndex = originalFileName.lastIndexOf('.');
        return extensionIndex > 0 ? originalFileName.substring(0, extensionIndex) : originalFileName;
    }

    private String resolveFileExtension(String originalFileName) {
        int extensionIndex = originalFileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == originalFileName.length() - 1) {
            return "";
        }
        return originalFileName.substring(extensionIndex).toLowerCase(java.util.Locale.ROOT);
    }

    private String buildValidationErrorMessage(Exception ex) {
        String message = rootMessage(ex);
        if (message == null || message.isBlank()) {
            return "Volcengine VOD validation failed";
        }
        return "Volcengine VOD validation failed: " + message;
    }

    private boolean isNoAvailableDomainError(String message) {
        return message != null && message.contains("ResourceNotFound.NoAvailableDomain");
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}

@FunctionalInterface
interface VodServiceFactory {
    IVodService create(String region) throws Exception;
}
