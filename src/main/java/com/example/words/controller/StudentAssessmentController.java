package com.example.words.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.words.dto.StudentAssessmentSummaryResponse;
import com.example.words.service.CurrentUserService;
import com.example.words.service.StudentAssessmentService;

@RestController
@RequestMapping("/api/students/me/assessments")
@PreAuthorize("hasRole('STUDENT')")
public class StudentAssessmentController {

    private final StudentAssessmentService assessmentService;
    private final CurrentUserService currentUserService;

    public StudentAssessmentController(
            StudentAssessmentService assessmentService,
            CurrentUserService currentUserService) {
        this.assessmentService = assessmentService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<StudentAssessmentSummaryResponse>> listPending() {
        return ResponseEntity.ok(assessmentService.listPending(currentUserService.getCurrentUser()));
    }

    @GetMapping("/history")
    public ResponseEntity<List<StudentAssessmentSummaryResponse>> listHistory() {
        return ResponseEntity.ok(assessmentService.listHistory(currentUserService.getCurrentUser()));
    }
}
