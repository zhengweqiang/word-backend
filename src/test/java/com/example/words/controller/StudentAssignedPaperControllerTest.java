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

import com.example.words.dto.SaveStudentPaperDraftRequest;
import com.example.words.dto.SubmitStudentPaperRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.UserRole;
import com.example.words.security.JwtAuthenticationFilter;
import com.example.words.service.CurrentUserService;
import com.example.words.service.StudentPaperAttemptService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(
        controllers = StudentAssignedPaperController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@Import(StudentAssignedPaperControllerTest.TestSecurityConfiguration.class)
class StudentAssignedPaperControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentPaperAttemptService service;

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
    @WithMockUser(roles = "TEACHER")
    void teachersCannotUseStudentPaperEndpoints() throws Exception {
        mockMvc.perform(get("/api/students/me/papers"))
                .andExpect(status().isForbidden());

        verify(service, never()).listAssigned(any());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void routesListOpenSaveSubmitAndResult() throws Exception {
        mockMvc.perform(get("/api/students/me/papers"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/students/me/papers/{attemptId}", 100L))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/students/me/papers/{attemptId}/draft", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3,\"answers\":[]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/students/me/papers/{attemptId}/submit", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3,\"answers\":[]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/students/me/papers/{attemptId}/result", 100L))
                .andExpect(status().isOk());

        verify(service).listAssigned(student);
        verify(service).open(100L, student);
        verify(service).saveDraft(eq(100L), any(SaveStudentPaperDraftRequest.class), eq(student));
        verify(service).submit(eq(100L), any(SubmitStudentPaperRequest.class), eq(student));
        verify(service).getResult(100L, student);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void rejectsDraftAndSubmitWithoutExpectedVersionBeforeService() throws Exception {
        mockMvc.perform(put("/api/students/me/papers/{attemptId}/draft", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/students/me/papers/{attemptId}/submit", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).saveDraft(any(), any(), any());
        verify(service, never()).submit(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void rejectsAnswerWithoutReleaseQuestionIdBeforeService() throws Exception {
        mockMvc.perform(post("/api/students/me/papers/{attemptId}/submit", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 3,
                                  "answers": [{"selectedAnswers": ["A"], "blankAnswers": []}]
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(service, never()).submit(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void mapsFrozenOptionValidationFailureToBadRequest() throws Exception {
        when(service.submit(eq(100L), any(SubmitStudentPaperRequest.class), eq(student)))
                .thenThrow(new BadRequestException("Choice answer contains an unknown option key"));

        mockMvc.perform(post("/api/students/me/papers/{attemptId}/submit", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 3,
                                  "answers": [{
                                    "releaseQuestionId": 1000,
                                    "selectedAnswers": ["Z"],
                                    "blankAnswers": []
                                  }]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Choice answer contains an unknown option key"));
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
