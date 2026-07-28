package com.example.words.controller;

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
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import com.example.words.dto.AddPaperQuestionRequest;
import com.example.words.dto.CreatePaperTemplateRequest;
import com.example.words.dto.PaperTemplateResponse;
import com.example.words.dto.PaperTemplateSearchRequest;
import com.example.words.dto.ReorderPaperQuestionsRequest;
import com.example.words.dto.UpdatePaperQuestionScoreRequest;
import com.example.words.dto.UpdatePaperTemplateRequest;
import com.example.words.model.AppUser;
import com.example.words.model.PaperTemplateStatus;
import com.example.words.model.UserRole;
import com.example.words.security.JwtAuthenticationFilter;
import com.example.words.service.CurrentUserService;
import com.example.words.service.PaperTemplateService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TeacherPaperTemplateController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@Import(TeacherPaperTemplateControllerTest.TestSecurityConfiguration.class)
class TeacherPaperTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaperTemplateService paperTemplateService;

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
    @WithMockUser(roles = "STUDENT")
    void studentsCannotAccessPaperManagement() throws Exception {
        mockMvc.perform(get("/api/teacher/papers"))
                .andExpect(status().isForbidden());

        verify(paperTemplateService, never()).search(any(), any());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void searchBindsFiltersAndPaging() throws Exception {
        when(paperTemplateService.search(any(), eq(actor))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/teacher/papers")
                        .param("keyword", "quiz")
                        .param("status", "READY")
                        .param("ownerUserId", "8")
                        .param("page", "2")
                        .param("size", "15"))
                .andExpect(status().isOk());

        ArgumentCaptor<PaperTemplateSearchRequest> captor =
                ArgumentCaptor.forClass(PaperTemplateSearchRequest.class);
        verify(paperTemplateService).search(captor.capture(), eq(actor));
        assertEquals("quiz", captor.getValue().getKeyword());
        assertEquals(PaperTemplateStatus.READY, captor.getValue().getStatus());
        assertEquals(8L, captor.getValue().getOwnerUserId());
        assertEquals(2, captor.getValue().getPage());
        assertEquals(15, captor.getValue().getSize());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void createAndUpdateBindValidatedBodies() throws Exception {
        when(paperTemplateService.create(any(), eq(actor))).thenReturn(response());
        when(paperTemplateService.update(eq(10L), any(), eq(actor))).thenReturn(response());

        mockMvc.perform(post("/api/teacher/papers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Quiz","instructions":"Read","shuffleQuestions":true,
                                 "shuffleOptions":false}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/teacher/papers/{paperId}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Quiz 2","instructions":"Read","shuffleQuestions":false,
                                 "shuffleOptions":true,"status":"READY"}
                                """))
                .andExpect(status().isOk());

        verify(paperTemplateService).create(any(CreatePaperTemplateRequest.class), eq(actor));
        verify(paperTemplateService).update(eq(10L), any(UpdatePaperTemplateRequest.class), eq(actor));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void questionEditorRoutesDelegateToService() throws Exception {
        when(paperTemplateService.addQuestion(eq(10L), any(), eq(actor))).thenReturn(response());
        when(paperTemplateService.reorderQuestions(eq(10L), any(), eq(actor))).thenReturn(response());
        when(paperTemplateService.updateQuestionScore(eq(10L), eq(20L), any(), eq(actor)))
                .thenReturn(response());
        when(paperTemplateService.removeQuestion(10L, 20L, actor)).thenReturn(response());

        mockMvc.perform(post("/api/teacher/papers/{paperId}/questions", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":50,\"score\":2.50}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/teacher/papers/{paperId}/questions/reorder", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paperQuestionIds\":[20,21]}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/teacher/papers/{paperId}/questions/{paperQuestionId}/score", 10L, 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4.25}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/teacher/papers/{paperId}/questions/{paperQuestionId}", 10L, 20L))
                .andExpect(status().isOk());

        verify(paperTemplateService).addQuestion(eq(10L), any(AddPaperQuestionRequest.class), eq(actor));
        verify(paperTemplateService).reorderQuestions(eq(10L), any(ReorderPaperQuestionsRequest.class), eq(actor));
        verify(paperTemplateService).updateQuestionScore(
                eq(10L), eq(20L), any(UpdatePaperQuestionScoreRequest.class), eq(actor));
        verify(paperTemplateService).removeQuestion(10L, 20L, actor);
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void previewCopyAndArchiveRoutesUseExpectedStatuses() throws Exception {
        when(paperTemplateService.preview(10L, actor)).thenReturn(response());
        when(paperTemplateService.copy(eq(10L), any(), eq(actor))).thenReturn(response());

        mockMvc.perform(get("/api/teacher/papers/{paperId}/preview", 10L))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/teacher/papers/{paperId}/copy", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Copy\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/teacher/papers/{paperId}/archive", 10L))
                .andExpect(status().isNoContent());

        verify(paperTemplateService).archive(10L, actor);
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void invalidPaperOrScoreBodyReturnsBadRequestBeforeService() throws Exception {
        mockMvc.perform(post("/api/teacher/papers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/teacher/papers/{paperId}/questions/{paperQuestionId}/score", 10L, 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":0}"))
                .andExpect(status().isBadRequest());

        verify(paperTemplateService, never()).create(any(), any());
        verify(paperTemplateService, never()).updateQuestionScore(any(), any(), any(), any());
    }

    private PaperTemplateResponse response() {
        PaperTemplateResponse response = new PaperTemplateResponse();
        response.setId(10L);
        response.setQuestions(List.of());
        return response;
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
