package com.example.words.controller;

import java.util.List;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import com.example.words.dto.StudentAssessmentStatus;
import com.example.words.dto.StudentAssessmentSummaryResponse;
import com.example.words.dto.StudentAssessmentType;
import com.example.words.model.AppUser;
import com.example.words.model.UserRole;
import com.example.words.security.JwtAuthenticationFilter;
import com.example.words.service.CurrentUserService;
import com.example.words.service.StudentAssessmentService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = StudentAssessmentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@Import(StudentAssessmentControllerTest.TestSecurityConfiguration.class)
class StudentAssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentAssessmentService service;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private AppUser student;

    @BeforeEach
    void setUp() {
        student = new AppUser();
        student.setId(20L);
        student.setRole(UserRole.STUDENT);
        when(currentUserService.getCurrentUser()).thenReturn(student);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void routesPendingAndHistoryWithAssessmentTypeInformation() throws Exception {
        StudentAssessmentSummaryResponse pending = summary(
                10L,
                StudentAssessmentType.PAPER_RELEASE_ATTEMPT,
                StudentAssessmentStatus.OVERDUE);
        pending.setPaperAttemptId(10L);
        pending.setPaperReleaseId(30L);
        pending.setScoreVisible(false);
        StudentAssessmentSummaryResponse history = summary(
                11L,
                StudentAssessmentType.LEGACY_GENERATED_EXAM,
                StudentAssessmentStatus.SUBMITTED);
        history.setLegacyExamId(11L);
        history.setScoreVisible(true);
        when(service.listPending(student)).thenReturn(List.of(pending));
        when(service.listHistory(student)).thenReturn(List.of(history));

        mockMvc.perform(get("/api/students/me/assessments/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assessmentType").value("PAPER_RELEASE_ATTEMPT"))
                .andExpect(jsonPath("$[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$[0].scoreVisible").value(false))
                .andExpect(jsonPath("$[0].paperAttemptId").value(10L))
                .andExpect(jsonPath("$[0].paperReleaseId").value(30L));
        mockMvc.perform(get("/api/students/me/assessments/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assessmentType").value("LEGACY_GENERATED_EXAM"))
                .andExpect(jsonPath("$[0].scoreVisible").value(true))
                .andExpect(jsonPath("$[0].legacyExamId").value(11L));

        verify(service).listPending(student);
        verify(service).listHistory(student);
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teachersCannotUseStudentAssessmentEndpoints() throws Exception {
        mockMvc.perform(get("/api/students/me/assessments/pending"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/students/me/assessments/history"))
                .andExpect(status().isForbidden());

        verify(service, never()).listPending(any());
        verify(service, never()).listHistory(any());
    }

    private StudentAssessmentSummaryResponse summary(
            Long id,
            StudentAssessmentType type,
            StudentAssessmentStatus status) {
        StudentAssessmentSummaryResponse response = new StudentAssessmentSummaryResponse();
        response.setAssessmentId(id);
        response.setAssessmentType(type);
        response.setStatus(status);
        response.setTitle("测验");
        return response;
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
