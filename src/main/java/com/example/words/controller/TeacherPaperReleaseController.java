package com.example.words.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.words.dto.InvalidatePaperReleaseRequest;
import com.example.words.dto.PaperReleaseResponse;
import com.example.words.dto.PaperReleaseQuestionStatResponse;
import com.example.words.dto.PaperReleaseResultOverviewResponse;
import com.example.words.dto.PaperReleaseStudentResultResponse;
import com.example.words.dto.PublishPaperRequest;
import com.example.words.dto.ReleasePaperResultsRequest;
import com.example.words.dto.SupersedePaperReleaseRequest;
import com.example.words.dto.WithdrawPaperReleaseRequest;
import com.example.words.service.CurrentUserService;
import com.example.words.service.PaperReleaseService;
import com.example.words.service.PaperResultReviewService;

@RestController
@RequestMapping("/api/teacher/paper-releases")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class TeacherPaperReleaseController {

    private final PaperReleaseService paperReleaseService;
    private final PaperResultReviewService paperResultReviewService;
    private final CurrentUserService currentUserService;

    public TeacherPaperReleaseController(
            PaperReleaseService paperReleaseService,
            PaperResultReviewService paperResultReviewService,
            CurrentUserService currentUserService) {
        this.paperReleaseService = paperReleaseService;
        this.paperResultReviewService = paperResultReviewService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<PaperReleaseResponse>> list() {
        return ResponseEntity.ok(paperResultReviewService.listReleases(
                currentUserService.getCurrentUser()));
    }

    @GetMapping("/{releaseId}")
    public ResponseEntity<PaperReleaseResponse> get(@PathVariable Long releaseId) {
        return ResponseEntity.ok(paperResultReviewService.getRelease(
                releaseId, currentUserService.getCurrentUser()));
    }

    @PostMapping
    public ResponseEntity<PaperReleaseResponse> publish(@Valid @RequestBody PublishPaperRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paperReleaseService.publish(
                request, currentUserService.getCurrentUser()));
    }

    @PostMapping("/{releaseId}/withdraw")
    public ResponseEntity<PaperReleaseResponse> withdraw(
            @PathVariable Long releaseId,
            @Valid @RequestBody WithdrawPaperReleaseRequest request) {
        return ResponseEntity.ok(paperReleaseService.withdraw(
                releaseId, request, currentUserService.getCurrentUser()));
    }

    @PostMapping("/{releaseId}/invalidate")
    public ResponseEntity<PaperReleaseResponse> invalidate(
            @PathVariable Long releaseId,
            @Valid @RequestBody InvalidatePaperReleaseRequest request) {
        return ResponseEntity.ok(paperReleaseService.invalidate(
                releaseId, request, currentUserService.getCurrentUser()));
    }

    @PostMapping("/{releaseId}/supersede")
    public ResponseEntity<PaperReleaseResponse> supersede(
            @PathVariable Long releaseId,
            @Valid @RequestBody SupersedePaperReleaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paperReleaseService.supersede(
                releaseId, request, currentUserService.getCurrentUser()));
    }

    @GetMapping("/{releaseId}/results")
    public ResponseEntity<PaperReleaseResultOverviewResponse> getResultOverview(
            @PathVariable Long releaseId) {
        return ResponseEntity.ok(paperResultReviewService.getOverview(
                releaseId, currentUserService.getCurrentUser()));
    }

    @GetMapping("/{releaseId}/results/students/{attemptId}")
    public ResponseEntity<PaperReleaseStudentResultResponse> getStudentResult(
            @PathVariable Long releaseId,
            @PathVariable Long attemptId) {
        return ResponseEntity.ok(paperResultReviewService.getStudentResult(
                releaseId, attemptId, currentUserService.getCurrentUser()));
    }

    @GetMapping("/{releaseId}/results/questions")
    public ResponseEntity<List<PaperReleaseQuestionStatResponse>> getQuestionStatistics(
            @PathVariable Long releaseId) {
        return ResponseEntity.ok(paperResultReviewService.getQuestionStatistics(
                releaseId, currentUserService.getCurrentUser()));
    }

    @PostMapping("/{releaseId}/results/release")
    public ResponseEntity<PaperReleaseResultOverviewResponse> releaseResults(
            @PathVariable Long releaseId,
            @Valid @RequestBody ReleasePaperResultsRequest request) {
        return ResponseEntity.ok(paperResultReviewService.releaseResults(
                releaseId, request, currentUserService.getCurrentUser()));
    }
}
