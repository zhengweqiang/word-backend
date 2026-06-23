package com.example.words.controller;

import com.example.words.dto.StudentVideoResponse;
import com.example.words.dto.VideoAccessResponse;
import com.example.words.model.Dictionary;
import com.example.words.service.CurrentUserService;
import com.example.words.service.DictionaryService;
import com.example.words.service.VideoAssetService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final CurrentUserService currentUserService;
    private final DictionaryService dictionaryService;
    private final VideoAssetService videoAssetService;

    public StudentController(
            CurrentUserService currentUserService,
            DictionaryService dictionaryService,
            VideoAssetService videoAssetService) {
        this.currentUserService = currentUserService;
        this.dictionaryService = dictionaryService;
        this.videoAssetService = videoAssetService;
    }

    @GetMapping("/me/dictionaries")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<Dictionary>> getMyAssignedDictionaries() {
        return ResponseEntity.ok(
                dictionaryService.findAssignedDictionariesForStudent(currentUserService.getCurrentUser().getId()));
    }

    @GetMapping("/me/videos/page")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Page<StudentVideoResponse>> getMyVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(videoAssetService.listStudentVideosPage(page, size, keyword));
    }

    @GetMapping("/me/videos/{id}/play")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<VideoAccessResponse> playVideo(@PathVariable Long id) {
        return ResponseEntity.ok(videoAssetService.getStudentPlayback(id));
    }
}
