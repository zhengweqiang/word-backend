package com.example.words.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.words.dto.SaveStudentPaperDraftRequest;
import com.example.words.dto.StudentAssignedPaperSummaryResponse;
import com.example.words.dto.StudentPaperAttemptResponse;
import com.example.words.dto.StudentPaperResultResponse;
import com.example.words.dto.SubmitStudentPaperRequest;
import com.example.words.dto.SubmitStudentPaperResponse;
import com.example.words.service.CurrentUserService;
import com.example.words.service.StudentPaperAttemptService;

@RestController
@RequestMapping("/api/students/me/papers")
@PreAuthorize("hasRole('STUDENT')")
public class StudentAssignedPaperController {

    private final StudentPaperAttemptService attemptService;
    private final CurrentUserService currentUserService;

    public StudentAssignedPaperController(
            StudentPaperAttemptService attemptService,
            CurrentUserService currentUserService) {
        this.attemptService = attemptService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<StudentAssignedPaperSummaryResponse>> listAssigned() {
        return ResponseEntity.ok(attemptService.listAssigned(currentUserService.getCurrentUser()));
    }

    @GetMapping("/{attemptId}")
    public ResponseEntity<StudentPaperAttemptResponse> open(@PathVariable Long attemptId) {
        return ResponseEntity.ok(attemptService.open(attemptId, currentUserService.getCurrentUser()));
    }

    @PutMapping("/{attemptId}/draft")
    public ResponseEntity<StudentPaperAttemptResponse> saveDraft(
            @PathVariable Long attemptId,
            @Valid @RequestBody SaveStudentPaperDraftRequest request) {
        return ResponseEntity.ok(attemptService.saveDraft(
                attemptId, request, currentUserService.getCurrentUser()));
    }

    @PostMapping("/{attemptId}/submit")
    public ResponseEntity<SubmitStudentPaperResponse> submit(
            @PathVariable Long attemptId,
            @Valid @RequestBody SubmitStudentPaperRequest request) {
        return ResponseEntity.ok(attemptService.submit(
                attemptId, request, currentUserService.getCurrentUser()));
    }

    @GetMapping("/{attemptId}/result")
    public ResponseEntity<StudentPaperResultResponse> getResult(@PathVariable Long attemptId) {
        return ResponseEntity.ok(attemptService.getResult(
                attemptId, currentUserService.getCurrentUser()));
    }
}
