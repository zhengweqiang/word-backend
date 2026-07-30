package com.example.words.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.words.dto.CreateQuestionCategoryRequest;
import com.example.words.dto.QuestionCategoryResponse;
import com.example.words.dto.UpdateQuestionCategoryRequest;
import com.example.words.model.AppUser;
import com.example.words.service.CurrentUserService;
import com.example.words.service.QuestionCategoryService;

@RestController
@RequestMapping("/api/teacher/question-categories")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class TeacherQuestionCategoryController {

    private final QuestionCategoryService questionCategoryService;
    private final CurrentUserService currentUserService;

    public TeacherQuestionCategoryController(
            QuestionCategoryService questionCategoryService,
            CurrentUserService currentUserService) {
        this.questionCategoryService = questionCategoryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<QuestionCategoryResponse>> list() {
        return ResponseEntity.ok(questionCategoryService.list(currentUserService.getCurrentUser()));
    }

    @PostMapping
    public ResponseEntity<QuestionCategoryResponse> create(
            @Valid @RequestBody CreateQuestionCategoryRequest request) {
        AppUser actor = currentUserService.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(questionCategoryService.create(request, actor));
    }

    @PutMapping("/{categoryId}")
    public ResponseEntity<QuestionCategoryResponse> update(
            @PathVariable Long categoryId,
            @Valid @RequestBody UpdateQuestionCategoryRequest request) {
        return ResponseEntity.ok(questionCategoryService.update(
                categoryId, request, currentUserService.getCurrentUser()));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable Long categoryId) {
        questionCategoryService.delete(categoryId, currentUserService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }
}
