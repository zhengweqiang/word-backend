package com.example.words.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.words.dto.CreateQuestionCategoryRequest;
import com.example.words.dto.QuestionCategoryResponse;
import com.example.words.dto.UpdateQuestionCategoryRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.QuestionCategory;
import com.example.words.model.UserRole;
import com.example.words.repository.QuestionCategoryRepository;

@Service
public class QuestionCategoryService {

    private final QuestionCategoryRepository repository;

    public QuestionCategoryService(QuestionCategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<QuestionCategoryResponse> list(AppUser actor) {
        ensureStaff(actor);
        return repository.findActiveOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public QuestionCategoryResponse create(CreateQuestionCategoryRequest request, AppUser actor) {
        ensureStaff(actor);
        String name = normalizedName(request == null ? null : request.getName());
        if (repository.existsActiveByNameIgnoreCase(name)) {
            throw new BadRequestException("Question category already exists");
        }

        QuestionCategory category = new QuestionCategory();
        category.setName(name);
        category.setCreatedByUserId(actor.getId());
        category.setLastModifiedByUserId(actor.getId());
        return toResponse(repository.save(category));
    }

    @Transactional
    public QuestionCategoryResponse update(Long id, UpdateQuestionCategoryRequest request, AppUser actor) {
        ensureStaff(actor);
        if (id == null) {
            throw new BadRequestException("Question category ID is required");
        }
        String name = normalizedName(request == null ? null : request.getName());
        if (repository.existsActiveByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BadRequestException("Question category already exists");
        }
        QuestionCategory category = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question category not found: " + id));
        category.setName(name);
        category.setLastModifiedByUserId(actor.getId());
        return toResponse(repository.save(category));
    }

    @Transactional
    public void delete(Long id, AppUser actor) {
        ensureStaff(actor);
        if (id == null) {
            throw new BadRequestException("Question category ID is required");
        }
        QuestionCategory category = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question category not found: " + id));
        category.setDeletedAt(LocalDateTime.now());
        category.setLastModifiedByUserId(actor.getId());
        repository.save(category);
    }

    private void ensureStaff(AppUser actor) {
        if (actor == null || (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.TEACHER)) {
            throw new AccessDeniedException("Only teachers or admins can manage question categories");
        }
    }

    private String normalizedName(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException("Question category name is required");
        }
        if (normalized.length() > 100) {
            throw new BadRequestException("Question category name must be at most 100 characters");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private QuestionCategoryResponse toResponse(QuestionCategory category) {
        return new QuestionCategoryResponse(
                category.getId(),
                category.getName(),
                category.getCreatedByUserId(),
                category.getLastModifiedByUserId(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
