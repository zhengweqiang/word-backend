package com.example.words.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import com.example.words.dto.ConfirmQuestionImportRequest;
import com.example.words.dto.QuestionImportConfirmResponse;
import com.example.words.dto.QuestionImportPreviewResponse;
import com.example.words.model.AppUser;
import com.example.words.model.UserRole;
import com.example.words.security.JwtAuthenticationFilter;
import com.example.words.service.CurrentUserService;
import com.example.words.service.QuestionImportService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TeacherQuestionImportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@Import(TeacherQuestionImportControllerTest.TestSecurityConfiguration.class)
class TeacherQuestionImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuestionImportService importService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private AppUser actor;

    @BeforeEach
    void setUp() {
        actor = new AppUser();
        actor.setId(7L);
        actor.setRole(UserRole.TEACHER);
        when(currentUserService.getCurrentUser()).thenReturn(actor);
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void previewBindsMultipartCsvAndReturnsCreated() throws Exception {
        QuestionImportPreviewResponse response = new QuestionImportPreviewResponse();
        response.setBatchId(40L);
        when(importService.preview(any(), eq(actor))).thenReturn(response);
        MockMultipartFile file = new MockMultipartFile(
                "file", "questions.csv", "text/csv", "questionType\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/teacher/question-imports/preview").file(file))
                .andExpect(status().isCreated());

        ArgumentCaptor<MultipartFile> captor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(importService).preview(captor.capture(), eq(actor));
        assertEquals("questions.csv", captor.getValue().getOriginalFilename());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void getReturnsPersistedPreview() throws Exception {
        when(importService.get(40L, actor)).thenReturn(new QuestionImportPreviewResponse());

        mockMvc.perform(get("/api/teacher/question-imports/{batchId}", 40L))
                .andExpect(status().isOk());

        verify(importService).get(40L, actor);
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void confirmBindsSelectedRowIds() throws Exception {
        when(importService.confirm(eq(40L), any(), eq(actor))).thenReturn(new QuestionImportConfirmResponse());

        mockMvc.perform(post("/api/teacher/question-imports/{batchId}/confirm", 40L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedRowIds\":[102,103]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ConfirmQuestionImportRequest> captor =
                ArgumentCaptor.forClass(ConfirmQuestionImportRequest.class);
        verify(importService).confirm(eq(40L), captor.capture(), eq(actor));
        assertEquals(List.of(102L, 103L), captor.getValue().getSelectedRowIds());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotUseImportEndpoints() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "questions.csv", "text/csv", "questionType\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/teacher/question-imports/preview").file(file))
                .andExpect(status().isForbidden());

        verify(importService, never()).preview(any(), any());
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
            return http.build();
        }
    }
}
