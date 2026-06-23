package com.example.words.service.video;

import com.example.words.exception.BadRequestException;
import com.example.words.model.VideoStorageProviderType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class VideoStorageGatewayRegistry {

    private final Map<VideoStorageProviderType, VideoStorageGateway> gateways;

    public VideoStorageGatewayRegistry(List<VideoStorageGateway> gatewayList) {
        this.gateways = new EnumMap<>(VideoStorageProviderType.class);
        for (VideoStorageGateway gateway : gatewayList) {
            this.gateways.put(gateway.providerType(), gateway);
        }
    }

    public VideoStorageGateway get(VideoStorageProviderType providerType) {
        VideoStorageGateway gateway = gateways.get(providerType);
        if (gateway == null) {
            throw new BadRequestException("Unsupported video storage provider: " + providerType);
        }
        return gateway;
    }
}
