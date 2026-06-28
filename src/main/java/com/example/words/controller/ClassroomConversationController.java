package com.example.words.controller;

import com.example.words.dto.ClassroomConversationResponse;
import com.example.words.service.ClassroomGroupFeedService;
import com.example.words.service.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classroom-conversations")
public class ClassroomConversationController {

    private final ClassroomGroupFeedService classroomGroupFeedService;
    private final CurrentUserService currentUserService;

    public ClassroomConversationController(
            ClassroomGroupFeedService classroomGroupFeedService,
            CurrentUserService currentUserService) {
        this.classroomGroupFeedService = classroomGroupFeedService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Page<ClassroomConversationResponse>> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(classroomGroupFeedService.listConversations(
                page,
                size,
                currentUserService.getCurrentUser()
        ));
    }
}
