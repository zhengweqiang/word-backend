package com.example.words.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import com.example.words.dto.InvalidatePaperReleaseRequest;
import com.example.words.dto.PublishPaperRequest;
import com.example.words.dto.SupersedePaperReleaseRequest;
import com.example.words.dto.WithdrawPaperReleaseRequest;
import com.example.words.model.AppUser;
import com.example.words.model.UserRole;
import com.example.words.security.JwtAuthenticationFilter;
import com.example.words.service.CurrentUserService;
import com.example.words.service.PaperReleaseService;
import com.example.words.service.PaperResultReviewService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TeacherPaperReleaseController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@Import(TeacherPaperReleaseControllerTest.TestSecurityConfiguration.class)
class TeacherPaperReleaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaperReleaseService paperReleaseService;

    @MockBean
    private PaperResultReviewService paperResultReviewService;

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
    void studentsCannotManagePaperReleases() throws Exception {
        mockMvc.perform(post("/api/teacher/paper-releases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paperId\":10,\"studentIds\":[11]}"))
                .andExpect(status().isForbidden());

        verify(paperReleaseService, never()).publish(any(), any());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/teacher/paper-releases/{releaseId}/results", 20L))
                .andExpect(status().isForbidden());
        verify(paperResultReviewService, never()).getOverview(any(), any());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void resultReviewRoutesDelegateWithCurrentActor() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/teacher/paper-releases/{releaseId}/results", 20L))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/teacher/paper-releases/{releaseId}/results/students/{attemptId}", 20L, 101L))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/teacher/paper-releases/{releaseId}/results/questions", 20L))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/teacher/paper-releases/{releaseId}/results/release", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultVisibility\":\"SCORE_AND_ANSWERS\"}"))
                .andExpect(status().isOk());

        verify(paperResultReviewService).getOverview(20L, actor);
        verify(paperResultReviewService).getStudentResult(20L, 101L, actor);
        verify(paperResultReviewService).getQuestionStatistics(20L, actor);
        verify(paperResultReviewService).releaseResults(
                eq(20L),
                org.mockito.ArgumentMatchers.argThat(request ->
                        request.getResultVisibility()
                                == com.example.words.model.PaperResultVisibility.SCORE_AND_ANSWERS),
                eq(actor));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void releaseReadRoutesDelegateWithCurrentActor() throws Exception {
        mockMvc.perform(get("/api/teacher/paper-releases"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/teacher/paper-releases/{releaseId}", 20L))
                .andExpect(status().isOk());

        verify(paperResultReviewService).listReleases(actor);
        verify(paperResultReviewService).getRelease(20L, actor);
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void releaseResultsRejectsMissingVisibilityBeforeService() throws Exception {
        mockMvc.perform(post("/api/teacher/paper-releases/{releaseId}/results/release", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(paperResultReviewService, never()).releaseResults(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void publishAndCorrectionRoutesBindValidatedBodies() throws Exception {
        mockMvc.perform(post("/api/teacher/paper-releases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paperId":10,"studentIds":[11],"classroomIds":[31],
                                 "startTime":"2026-07-30T09:00:00","deadline":"2026-07-30T10:00:00"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/teacher/paper-releases/{releaseId}/withdraw", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Published by mistake\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/teacher/paper-releases/{releaseId}/invalidate", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Wrong key\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/teacher/paper-releases/{releaseId}/supersede", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Corrected\",\"showOriginalToStudents\":true}"))
                .andExpect(status().isCreated());

        verify(paperReleaseService).publish(any(PublishPaperRequest.class), eq(actor));
        verify(paperReleaseService).withdraw(eq(20L), any(WithdrawPaperReleaseRequest.class), eq(actor));
        verify(paperReleaseService).invalidate(eq(20L), any(InvalidatePaperReleaseRequest.class), eq(actor));
        verify(paperReleaseService).supersede(eq(20L), any(SupersedePaperReleaseRequest.class), eq(actor));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void rejectsMissingTargetsAndBlankCorrectionReasonsBeforeService() throws Exception {
        mockMvc.perform(post("/api/teacher/paper-releases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paperId\":10}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/teacher/paper-releases/{releaseId}/withdraw", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());

        verify(paperReleaseService, never()).publish(any(), any());
        verify(paperReleaseService, never()).withdraw(any(), any(), any());
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
            return http.build();
        }
    }
}
