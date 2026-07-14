package com.example.words.controller;

import com.example.words.dto.ClassroomGroupFeedMessageResponse;
import com.example.words.dto.CreateClassroomGroupFeedTextMessageRequest;
import com.example.words.dto.ShareClassroomGroupFeedDictionaryRequest;
import com.example.words.dto.ShareClassroomGroupFeedStudyPlanRequest;
import com.example.words.dto.ShareClassroomGroupFeedVideoRequest;
import com.example.words.dto.VideoAccessResponse;
import com.example.words.model.ClassroomGroupFeedMessageType;
import com.example.words.service.ClassroomGroupFeedService;
import com.example.words.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classrooms/{classroomId}/group-feed")
public class ClassroomGroupFeedController {

    private final ClassroomGroupFeedService classroomGroupFeedService;
    private final CurrentUserService currentUserService;

    public ClassroomGroupFeedController(
            ClassroomGroupFeedService classroomGroupFeedService,
            CurrentUserService currentUserService) {
        this.classroomGroupFeedService = classroomGroupFeedService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/messages")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public ResponseEntity<Page<ClassroomGroupFeedMessageResponse>> listMessages(
            @PathVariable Long classroomId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ClassroomGroupFeedMessageType messageType) {
        return ResponseEntity.ok(classroomGroupFeedService.listMessages(
                classroomId,
                page,
                size,
                messageType,
                currentUserService.getCurrentUser()
        ));
    }

    @PostMapping("/messages")
    @PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
    public ResponseEntity<ClassroomGroupFeedMessageResponse> createTextMessage(
            @PathVariable Long classroomId,
            @Valid @RequestBody CreateClassroomGroupFeedTextMessageRequest request) {
        return ResponseEntity.ok(classroomGroupFeedService.createTextMessage(
                classroomId,
                request,
                currentUserService.getCurrentUser()
        ));
    }

    @PostMapping("/dictionaries")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ClassroomGroupFeedMessageResponse> shareDictionary(
            @PathVariable Long classroomId,
            @Valid @RequestBody ShareClassroomGroupFeedDictionaryRequest request) {
        return ResponseEntity.ok(classroomGroupFeedService.shareDictionary(
                classroomId,
                request,
                currentUserService.getCurrentUser()
        ));
    }

    @PostMapping("/videos")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ClassroomGroupFeedMessageResponse> shareVideo(
            @PathVariable Long classroomId,
            @Valid @RequestBody ShareClassroomGroupFeedVideoRequest request) {
        return ResponseEntity.ok(classroomGroupFeedService.shareVideo(
                classroomId,
                request,
                currentUserService.getCurrentUser()
        ));
    }

    @GetMapping("/videos/{videoId}/play")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public ResponseEntity<VideoAccessResponse> playVideo(
            @PathVariable Long classroomId,
            @PathVariable Long videoId) {
        return ResponseEntity.ok(classroomGroupFeedService.getVideoPlayback(
                classroomId,
                videoId,
                currentUserService.getCurrentUser()
        ));
    }

    @PostMapping("/study-plans")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ClassroomGroupFeedMessageResponse> shareStudyPlan(
            @PathVariable Long classroomId,
            @Valid @RequestBody ShareClassroomGroupFeedStudyPlanRequest request) {
        return ResponseEntity.ok(classroomGroupFeedService.shareStudyPlan(
                classroomId,
                request,
                currentUserService.getCurrentUser()
        ));
    }
}
