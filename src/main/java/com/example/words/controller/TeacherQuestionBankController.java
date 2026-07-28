package com.example.words.controller;

import com.example.words.dto.CopyQuestionRequest;
import com.example.words.dto.CreateQuestionRequest;
import com.example.words.dto.QuestionBankItemResponse;
import com.example.words.dto.QuestionBankSearchRequest;
import com.example.words.dto.UpdateQuestionRequest;
import com.example.words.model.AppUser;
import com.example.words.service.CurrentUserService;
import com.example.words.service.QuestionBankService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/questions")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class TeacherQuestionBankController {

    private final QuestionBankService questionBankService;
    private final CurrentUserService currentUserService;

    public TeacherQuestionBankController(
            QuestionBankService questionBankService,
            CurrentUserService currentUserService) {
        this.questionBankService = questionBankService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<Page<QuestionBankItemResponse>> search(
            @Valid @ModelAttribute QuestionBankSearchRequest request) {
        return ResponseEntity.ok(questionBankService.search(request, currentUserService.getCurrentUser()));
    }

    @PostMapping
    public ResponseEntity<QuestionBankItemResponse> create(@Valid @RequestBody CreateQuestionRequest request) {
        AppUser actor = currentUserService.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(questionBankService.create(request, actor));
    }

    @PutMapping("/{questionId}")
    public ResponseEntity<QuestionBankItemResponse> update(
            @PathVariable Long questionId,
            @Valid @RequestBody UpdateQuestionRequest request) {
        AppUser actor = currentUserService.getCurrentUser();
        return ResponseEntity.ok(questionBankService.update(questionId, request, actor));
    }

    @PostMapping("/{questionId}/copy")
    public ResponseEntity<QuestionBankItemResponse> copy(
            @PathVariable Long questionId,
            @Valid @RequestBody(required = false) CopyQuestionRequest request) {
        AppUser actor = currentUserService.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(questionBankService.copy(questionId, request, actor));
    }

    @PatchMapping("/{questionId}/archive")
    public ResponseEntity<Void> archive(@PathVariable Long questionId) {
        questionBankService.archive(questionId, currentUserService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }
}
