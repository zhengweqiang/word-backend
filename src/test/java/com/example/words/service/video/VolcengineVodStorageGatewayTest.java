package com.example.words.service.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.exception.BadGatewayException;
import com.example.words.exception.BadRequestException;
import com.example.words.model.VideoStorageConfig;
import com.example.words.model.VideoStorageProviderType;
import com.example.words.service.VideoStorageConfigCryptoService;
import com.volcengine.service.base.model.base.ResponseError;
import com.volcengine.service.base.model.base.ResponseMetadata;
import com.volcengine.service.vod.IVodService;
import com.volcengine.service.vod.model.business.VodPlayInfo;
import com.volcengine.service.vod.model.business.VodPlayInfoModel;
import com.volcengine.service.vod.model.business.VodGetMediaInfosData;
import com.volcengine.service.vod.model.business.VodMediaInfo;
import com.volcengine.service.vod.model.request.VodGetPlayInfoRequest;
import com.volcengine.service.vod.model.request.VodUpdateMediaPublishStatusRequest;
import com.volcengine.service.vod.model.response.VodGetMediaInfosResponse;
import com.volcengine.service.vod.model.response.VodGetPlayInfoResponse;
import com.volcengine.service.vod.model.response.VodUpdateMediaPublishStatusResponse;
import java.lang.reflect.Method;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VolcengineVodStorageGatewayTest {

    private VideoStorageConfigCryptoService cryptoService;
    private VolcengineVodStorageGateway gateway;

    @BeforeEach
    void setUp() {
        cryptoService = new VideoStorageConfigCryptoService(
                Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes())
        );
        gateway = new VolcengineVodStorageGateway(cryptoService);
    }

    @Test
    void providerTypeShouldBeVolcengineVod() {
        assertEquals(VideoStorageProviderType.VOLCENGINE_VOD, gateway.providerType());
    }

    @Test
    void uploadFileExtensionShouldUseVolcengineStyle() throws Exception {
        Method method = VolcengineVodStorageGateway.class.getDeclaredMethod("resolveFileExtension", String.class);
        method.setAccessible(true);

        assertEquals(".mp4", method.invoke(gateway, "Lesson.MP4"));
    }

    @Test
    void publishMediaShouldRequestPublishedStatus() throws Exception {
        IVodService vodService = org.mockito.Mockito.mock(IVodService.class);
        when(vodService.updateMediaPublishStatus(any())).thenReturn(
                VodUpdateMediaPublishStatusResponse.newBuilder().build()
        );
        gateway = new VolcengineVodStorageGateway(cryptoService, region -> vodService);

        gateway.publishMedia(readyConfig(), "v02f81g10001");

        ArgumentCaptor<VodUpdateMediaPublishStatusRequest> captor =
                ArgumentCaptor.forClass(VodUpdateMediaPublishStatusRequest.class);
        verify(vodService).updateMediaPublishStatus(captor.capture());
        assertEquals("v02f81g10001", captor.getValue().getVid());
        assertEquals("Published", captor.getValue().getStatus());
    }

    @Test
    void unpublishMediaShouldRequestUnpublishedStatus() throws Exception {
        IVodService vodService = org.mockito.Mockito.mock(IVodService.class);
        when(vodService.updateMediaPublishStatus(any())).thenReturn(
                VodUpdateMediaPublishStatusResponse.newBuilder().build()
        );
        gateway = new VolcengineVodStorageGateway(cryptoService, region -> vodService);

        gateway.unpublishMedia(readyConfig(), "v02f81g10001");

        ArgumentCaptor<VodUpdateMediaPublishStatusRequest> captor =
                ArgumentCaptor.forClass(VodUpdateMediaPublishStatusRequest.class);
        verify(vodService).updateMediaPublishStatus(captor.capture());
        assertEquals("v02f81g10001", captor.getValue().getVid());
        assertEquals("Unpublished", captor.getValue().getStatus());
    }

    @Test
    void describeMediaShouldRequestHttpPlayUrlForBrowserPreview() throws Exception {
        IVodService vodService = mock(IVodService.class);
        VodGetMediaInfosResponse mediaInfosResponse = VodGetMediaInfosResponse.newBuilder()
                .setResult(VodGetMediaInfosData.newBuilder()
                        .addMediaInfoList(VodMediaInfo.newBuilder().build())
                        .build())
                .build();
        VodGetPlayInfoResponse playInfoResponse = VodGetPlayInfoResponse.newBuilder()
                .setResult(VodPlayInfoModel.newBuilder()
                        .addPlayInfoList(VodPlayInfo.newBuilder()
                                .setDefinition("360p")
                                .setMainPlayUrl("http://vedio.geniuspark.tech/video.mp4")
                                .build())
                        .build())
                .build();
        when(vodService.getMediaInfos(any())).thenReturn(mediaInfosResponse);
        when(vodService.getPlayInfo(any())).thenReturn(playInfoResponse);
        gateway = new VolcengineVodStorageGateway(cryptoService, region -> vodService);

        gateway.describeMedia(readyConfig(), "v02f81g10001");

        ArgumentCaptor<VodGetPlayInfoRequest> captor = ArgumentCaptor.forClass(VodGetPlayInfoRequest.class);
        verify(vodService).getPlayInfo(captor.capture());
        assertEquals("0", captor.getValue().getSsl());
    }

    @Test
    void publishMediaShouldRejectVolcengineErrorResponse() throws Exception {
        IVodService vodService = org.mockito.Mockito.mock(IVodService.class);
        when(vodService.updateMediaPublishStatus(any())).thenReturn(
                VodUpdateMediaPublishStatusResponse.newBuilder()
                        .setResponseMetadata(ResponseMetadata.newBuilder()
                                .setError(ResponseError.newBuilder()
                                        .setCode("InvalidParameter")
                                        .setMessage("invalid vid")))
                        .build()
        );
        gateway = new VolcengineVodStorageGateway(cryptoService, region -> vodService);

        BadGatewayException exception = assertThrows(
                BadGatewayException.class,
                () -> gateway.publishMedia(readyConfig(), "v02f81g10001")
        );

        assertEquals("Volcengine VOD publish failed: invalid vid", exception.getMessage());
    }

    @Test
    void resolvePlayUrlShouldFallBackToBackupPlayUrl() throws Exception {
        Method method = VolcengineVodStorageGateway.class.getDeclaredMethod("resolvePlayUrl", VodPlayInfoModel.class);
        method.setAccessible(true);
        VodPlayInfoModel playInfoModel = VodPlayInfoModel.newBuilder()
                .addPlayInfoList(VodPlayInfo.newBuilder()
                        .setDefinition("720p")
                        .setMainPlayUrl("")
                        .setBackupPlayUrl("https://backup.example.com/720p.mp4")
                        .setHeight(720)
                        .setBitrate(1200)
                        .build())
                .addPlayInfoList(VodPlayInfo.newBuilder()
                        .setDefinition("360p")
                        .setMainPlayUrl("")
                        .setBackupPlayUrl("https://backup.example.com/360p.mp4")
                        .setHeight(360)
                        .setBitrate(600)
                        .build())
                .build();

        assertEquals("https://backup.example.com/360p.mp4", method.invoke(gateway, playInfoModel));
    }

    @Test
    void validateShouldRejectMissingSpaceNameBeforeCallingVolcengine() {
        VideoStorageConfig config = readyConfig();
        config.setSpaceName(" ");

        BadRequestException exception = assertThrows(BadRequestException.class, () -> gateway.validate(config));

        assertEquals("spaceName is required for Volcengine VOD", exception.getMessage());
    }

    @Test
    void validateShouldRejectPlaceholderSecretsBeforeCallingVolcengine() {
        VideoStorageConfig config = readyConfig();
        config.setSecretIdEncrypted(cryptoService.encrypt("1"));
        config.setSecretKeyEncrypted(cryptoService.encrypt("1"));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> gateway.validate(config));

        assertEquals(
                "Current video storage config still uses placeholder AccessKeyId/SecretAccessKey. "
                        + "Please save real Volcengine VOD credentials before testing",
                exception.getMessage()
        );
    }

    private VideoStorageConfig readyConfig() {
        VideoStorageConfig config = new VideoStorageConfig();
        config.setRegion("cn-north-1");
        config.setSpaceName("learning-space");
        config.setSecretIdEncrypted(cryptoService.encrypt("AKLTFAKEACCESSKEY"));
        config.setSecretKeyEncrypted(cryptoService.encrypt("FAKESECRETACCESSKEY"));
        return config;
    }
}
