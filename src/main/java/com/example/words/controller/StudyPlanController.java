package com.example.words.controller;

import com.example.words.dto.AppendStudyPlanStudentsRequest;
import com.example.words.dto.CreateStudyPlanRequest;
import com.example.words.dto.StudyPlanOverviewResponse;
import com.example.words.dto.StudyPlanResponse;
import com.example.words.dto.StudyPlanStudentAttentionResponse;
import com.example.words.dto.StudyPlanStudentSummaryResponse;
import com.example.words.service.CurrentUserService;
import com.example.words.service.StudyPlanService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study-plans")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;
    private final CurrentUserService currentUserService;

    public StudyPlanController(StudyPlanService studyPlanService, CurrentUserService currentUserService) {
        this.studyPlanService = studyPlanService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<StudyPlanResponse> createStudyPlan(@Valid @RequestBody CreateStudyPlanRequest request) {
        return ResponseEntity.ok(studyPlanService.createStudyPlan(request, currentUserService.getCurrentUser()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<List<StudyPlanResponse>> listStudyPlans() {
        return ResponseEntity.ok(studyPlanService.listVisibleStudyPlans(currentUserService.getCurrentUser()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<StudyPlanResponse> getStudyPlan(@PathVariable Long id) {
        return ResponseEntity.ok(studyPlanService.getStudyPlan(id, currentUserService.getCurrentUser()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<StudyPlanResponse> publishStudyPlan(@PathVariable Long id) {
        return ResponseEntity.ok(studyPlanService.publishStudyPlan(id, currentUserService.getCurrentUser()));
    }

    @PostMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<StudyPlanResponse> appendStudents(
            @PathVariable Long id,
            @Valid @RequestBody AppendStudyPlanStudentsRequest request) {
        return ResponseEntity.ok(studyPlanService.appendStudents(id, request, currentUserService.getCurrentUser()));
    }

    @GetMapping("/{id}/overview")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<StudyPlanOverviewResponse> getOverview(@PathVariable Long id) {
        return ResponseEntity.ok(studyPlanService.getOverview(id, currentUserService.getCurrentUser()));
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<List<StudyPlanStudentSummaryResponse>> listPlanStudents(@PathVariable Long id) {
        return ResponseEntity.ok(studyPlanService.listPlanStudents(id, currentUserService.getCurrentUser()));
    }

    @GetMapping("/{id}/students/{studentId}/attention")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<StudyPlanStudentAttentionResponse> getStudentAttention(
            @PathVariable Long id,
            @PathVariable Long studentId) {
        return ResponseEntity.ok(
                studyPlanService.getPlanStudentAttention(id, studentId, currentUserService.getCurrentUser()));
    }
}
