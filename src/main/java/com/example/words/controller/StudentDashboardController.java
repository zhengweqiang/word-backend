package com.example.words.controller;

import com.example.words.dto.StudentDashboardRecordRequest;
import com.example.words.dto.StudentDashboardResponse;
import com.example.words.service.CurrentUserService;
import com.example.words.service.StudentDashboardService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/me/dashboard")
@PreAuthorize("hasRole('STUDENT')")
public class StudentDashboardController {

    private final StudentDashboardService dashboardService;
    private final CurrentUserService currentUserService;

    public StudentDashboardController(
            StudentDashboardService dashboardService,
            CurrentUserService currentUserService) {
        this.dashboardService = dashboardService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<StudentDashboardResponse> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard(currentUserService.getCurrentUser()));
    }

    @PostMapping("/records")
    public ResponseEntity<StudentDashboardResponse> record(
            @Valid @RequestBody StudentDashboardRecordRequest request) {
        return ResponseEntity.ok(dashboardService.record(request, currentUserService.getCurrentUser()));
    }
}
