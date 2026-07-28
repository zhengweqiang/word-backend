package com.example.words.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.words.dto.AddPaperQuestionRequest;
import com.example.words.dto.CopyPaperTemplateRequest;
import com.example.words.dto.CreatePaperTemplateRequest;
import com.example.words.dto.PaperTemplateResponse;
import com.example.words.dto.PaperTemplateSearchRequest;
import com.example.words.dto.ReorderPaperQuestionsRequest;
import com.example.words.dto.UpdatePaperQuestionScoreRequest;
import com.example.words.dto.UpdatePaperTemplateRequest;
import com.example.words.model.AppUser;
import com.example.words.service.CurrentUserService;
import com.example.words.service.PaperTemplateService;

@RestController
@RequestMapping("/api/teacher/papers")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class TeacherPaperTemplateController {

    private final PaperTemplateService paperTemplateService;
    private final CurrentUserService currentUserService;

    public TeacherPaperTemplateController(
            PaperTemplateService paperTemplateService,
            CurrentUserService currentUserService) {
        this.paperTemplateService = paperTemplateService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<Page<PaperTemplateResponse>> search(
            @Valid @ModelAttribute PaperTemplateSearchRequest request) {
        return ResponseEntity.ok(paperTemplateService.search(request, currentUserService.getCurrentUser()));
    }

    @PostMapping
    public ResponseEntity<PaperTemplateResponse> create(
            @Valid @RequestBody CreatePaperTemplateRequest request) {
        AppUser actor = currentUserService.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(paperTemplateService.create(request, actor));
    }

    @PutMapping("/{paperId}")
    public ResponseEntity<PaperTemplateResponse> update(
            @PathVariable Long paperId,
            @Valid @RequestBody UpdatePaperTemplateRequest request) {
        return ResponseEntity.ok(paperTemplateService.update(
                paperId, request, currentUserService.getCurrentUser()));
    }

    @GetMapping("/{paperId}/preview")
    public ResponseEntity<PaperTemplateResponse> preview(@PathVariable Long paperId) {
        return ResponseEntity.ok(paperTemplateService.preview(paperId, currentUserService.getCurrentUser()));
    }

    @PostMapping("/{paperId}/copy")
    public ResponseEntity<PaperTemplateResponse> copy(
            @PathVariable Long paperId,
            @Valid @RequestBody(required = false) CopyPaperTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paperTemplateService.copy(
                paperId, request, currentUserService.getCurrentUser()));
    }

    @PatchMapping("/{paperId}/archive")
    public ResponseEntity<Void> archive(@PathVariable Long paperId) {
        paperTemplateService.archive(paperId, currentUserService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{paperId}/questions")
    public ResponseEntity<PaperTemplateResponse> addQuestion(
            @PathVariable Long paperId,
            @Valid @RequestBody AddPaperQuestionRequest request) {
        return ResponseEntity.ok(paperTemplateService.addQuestion(
                paperId, request, currentUserService.getCurrentUser()));
    }

    @PutMapping("/{paperId}/questions/reorder")
    public ResponseEntity<PaperTemplateResponse> reorderQuestions(
            @PathVariable Long paperId,
            @Valid @RequestBody ReorderPaperQuestionsRequest request) {
        return ResponseEntity.ok(paperTemplateService.reorderQuestions(
                paperId, request, currentUserService.getCurrentUser()));
    }

    @PatchMapping("/{paperId}/questions/{paperQuestionId}/score")
    public ResponseEntity<PaperTemplateResponse> updateQuestionScore(
            @PathVariable Long paperId,
            @PathVariable Long paperQuestionId,
            @Valid @RequestBody UpdatePaperQuestionScoreRequest request) {
        return ResponseEntity.ok(paperTemplateService.updateQuestionScore(
                paperId, paperQuestionId, request, currentUserService.getCurrentUser()));
    }

    @DeleteMapping("/{paperId}/questions/{paperQuestionId}")
    public ResponseEntity<PaperTemplateResponse> removeQuestion(
            @PathVariable Long paperId,
            @PathVariable Long paperQuestionId) {
        return ResponseEntity.ok(paperTemplateService.removeQuestion(
                paperId, paperQuestionId, currentUserService.getCurrentUser()));
    }
}
