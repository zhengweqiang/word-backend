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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import com.example.words.dto.ExamHistoryItemDto;
import com.example.words.model.AppUser;
import com.example.words.model.UserRole;
import com.example.words.security.JwtAuthenticationFilter;
import com.example.words.service.CurrentUserService;
import com.example.words.service.ExamService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ExamController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@Import(ExamControllerTest.TestSecurityConfiguration.class)
class ExamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExamService examService;

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
    void historyAlwaysUsesAuthenticatedActorAwareServiceMethod() throws Exception {
        ExamHistoryItemDto item = new ExamHistoryItemDto();
        item.setExamId(88L);
        item.setStatus("SUBMITTED");
        when(examService.getExamHistory((Long) null, student)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/exams/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].examId").value(88L));

        verify(examService).getExamHistory((Long) null, student);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void creatorOnlyMalformedExamDenialRemainsForbiddenAtApiBoundary() throws Exception {
        when(examService.getExam(88L, student))
                .thenThrow(new AccessDeniedException("You do not have access to this exam"));

        mockMvc.perform(get("/api/exams/{examId}", 88L))
                .andExpect(status().isForbidden());

        verify(examService).getExam(88L, student);
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
