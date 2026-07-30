package com.example.words.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.example.words.dto.CreateQuestionCategoryRequest;
import com.example.words.dto.QuestionCategoryResponse;
import com.example.words.dto.UpdateQuestionCategoryRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.QuestionCategory;
import com.example.words.model.UserRole;
import com.example.words.repository.QuestionCategoryRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionCategoryServiceTest {

    @Mock
    private QuestionCategoryRepository repository;

    private QuestionCategoryService service;

    @BeforeEach
    void setUp() {
        service = new QuestionCategoryService(repository);
        lenient().when(repository.save(any(QuestionCategory.class))).thenAnswer(invocation -> {
            QuestionCategory category = invocation.getArgument(0);
            if (category.getId() == null) {
                category.setId(10L);
            }
            return category;
        });
    }

    @Test
    void staffCanCreateUpdateListAndSoftDeleteQuestionCategories() {
        QuestionCategory created = category(10L, "听力", 7L, null);
        when(repository.existsActiveByNameIgnoreCase("听力")).thenReturn(false);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(created));
        when(repository.findActiveOrderByNameAsc()).thenReturn(List.of(created));

        QuestionCategoryResponse createResponse = service.create(
                new CreateQuestionCategoryRequest(" 听力 "), user(7L, UserRole.TEACHER));

        assertEquals("听力", createResponse.getName());
        assertEquals(7L, createResponse.getCreatedByUserId());
        assertEquals(List.of("听力"), service.list(user(1L, UserRole.ADMIN)).stream()
                .map(QuestionCategoryResponse::getName)
                .toList());

        QuestionCategoryResponse updateResponse = service.update(
                10L, new UpdateQuestionCategoryRequest(" 阅读 "), user(7L, UserRole.TEACHER));
        assertEquals("阅读", updateResponse.getName());
        assertEquals(7L, created.getLastModifiedByUserId());

        service.delete(10L, user(1L, UserRole.ADMIN));
        assertNotNull(created.getDeletedAt());
        assertEquals(1L, created.getLastModifiedByUserId());

        ArgumentCaptor<QuestionCategory> captor = ArgumentCaptor.forClass(QuestionCategory.class);
        verify(repository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertEquals("听力", captor.getAllValues().get(0).getName());
    }

    @Test
    void createRejectsBlankDuplicateAndStudentRequests() {
        assertThrows(BadRequestException.class,
                () -> service.create(new CreateQuestionCategoryRequest(" "), user(7L, UserRole.TEACHER)));

        when(repository.existsActiveByNameIgnoreCase("听力")).thenReturn(true);
        assertThrows(BadRequestException.class,
                () -> service.create(new CreateQuestionCategoryRequest("听力"), user(7L, UserRole.TEACHER)));

        assertThrows(AccessDeniedException.class, () -> service.list(user(20L, UserRole.STUDENT)));
        verify(repository, never()).findActiveOrderByNameAsc();
    }

    private QuestionCategory category(Long id, String name, Long createdByUserId, LocalDateTime deletedAt) {
        QuestionCategory category = new QuestionCategory();
        category.setId(id);
        category.setName(name);
        category.setCreatedByUserId(createdByUserId);
        category.setDeletedAt(deletedAt);
        return category;
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
