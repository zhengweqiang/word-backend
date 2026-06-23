package com.example.words.controller;

import com.example.words.dto.VideoAccessResponse;
import com.example.words.dto.VideoResponse;
import com.example.words.model.ResourceScopeType;
import com.example.words.model.VideoPublishStatus;
import com.example.words.model.VideoStatus;
import com.example.words.service.VideoAssetService;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/videos")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class VideoController {

    private final VideoAssetService videoAssetService;

    public VideoController(VideoAssetService videoAssetService) {
        this.videoAssetService = videoAssetService;
    }

    @GetMapping("/page")
    public ResponseEntity<Page<VideoResponse>> listPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VideoStatus status,
            @RequestParam(required = false) VideoPublishStatus publishStatus,
            @RequestParam(required = false) ResourceScopeType scopeType) {
        return ResponseEntity.ok(videoAssetService.listVisibleVideosPage(
                page,
                size,
                keyword,
                status,
                publishStatus,
                scopeType
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VideoResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(videoAssetService.getVideoResponse(id));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description) {
        return ResponseEntity.ok(videoAssetService.upload(file, title, description));
    }

    @GetMapping("/{id}/access")
    public ResponseEntity<VideoAccessResponse> access(@PathVariable Long id) {
        return ResponseEntity.ok(videoAssetService.getAccess(id));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<VideoResponse> sync(@PathVariable Long id) {
        return ResponseEntity.ok(videoAssetService.sync(id));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<VideoResponse> publish(@PathVariable Long id) {
        return ResponseEntity.ok(videoAssetService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<VideoResponse> unpublish(@PathVariable Long id) {
        return ResponseEntity.ok(videoAssetService.unpublish(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        videoAssetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
