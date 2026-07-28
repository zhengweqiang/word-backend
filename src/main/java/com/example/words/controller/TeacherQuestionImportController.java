package com.example.words.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.words.dto.ConfirmQuestionImportRequest;
import com.example.words.dto.QuestionImportConfirmResponse;
import com.example.words.dto.QuestionImportPreviewResponse;
import com.example.words.service.CurrentUserService;
import com.example.words.service.QuestionImportService;

@RestController
@RequestMapping("/api/teacher/question-imports")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class TeacherQuestionImportController {

    private final QuestionImportService questionImportService;
    private final CurrentUserService currentUserService;

    public TeacherQuestionImportController(
            QuestionImportService questionImportService,
            CurrentUserService currentUserService) {
        this.questionImportService = questionImportService;
        this.currentUserService = currentUserService;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<QuestionImportPreviewResponse> preview(@RequestParam("file") MultipartFile file) {
        QuestionImportPreviewResponse response = questionImportService.preview(
                file, currentUserService.getCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{batchId}")
    public ResponseEntity<QuestionImportPreviewResponse> get(@PathVariable Long batchId) {
        return ResponseEntity.ok(questionImportService.get(batchId, currentUserService.getCurrentUser()));
    }

    @PostMapping("/{batchId}/confirm")
    public ResponseEntity<QuestionImportConfirmResponse> confirm(
            @PathVariable Long batchId,
            @Valid @RequestBody ConfirmQuestionImportRequest request) {
        return ResponseEntity.ok(questionImportService.confirm(
                batchId, request, currentUserService.getCurrentUser()));
    }
}
