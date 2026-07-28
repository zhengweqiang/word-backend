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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import com.example.words.dto.CopyQuestionRequest;
import com.example.words.dto.CreateQuestionRequest;
import com.example.words.dto.QuestionBankItemResponse;
import com.example.words.dto.QuestionBankSearchRequest;
import com.example.words.dto.UpdateQuestionRequest;
import com.example.words.model.AppUser;
import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.QuestionType;
import com.example.words.model.UserRole;
import com.example.words.security.JwtAuthenticationFilter;
import com.example.words.service.CurrentUserService;
import com.example.words.service.QuestionBankService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TeacherQuestionBankController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@Import(TeacherQuestionBankControllerTest.TestSecurityConfiguration.class)
class TeacherQuestionBankControllerTest {

    private static final String VALID_CREATE_JSON = """
            {
              "questionType": "FILL_IN_BLANK",
              "stem": "Capital of France",
              "acceptedAnswers": ["Paris"],
              "defaultScore": 2.50
            }
            """;
    private static final String VALID_UPDATE_JSON = """
            {
              "questionType": "FILL_IN_BLANK",
              "stem": "Capital of France",
              "acceptedAnswers": ["Paris"],
              "defaultScore": 2.50,
              "status": "ACTIVE"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuestionBankService questionBankService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private AppUser actor;

    @BeforeEach
    void setUp() {
        actor = user(7L, UserRole.TEACHER);
        when(currentUserService.getCurrentUser()).thenReturn(actor);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessQuestionBank() throws Exception {
        when(questionBankService.search(any(), eq(actor))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/teacher/questions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherCanAccessQuestionBank() throws Exception {
        when(questionBankService.search(any(), eq(actor))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/teacher/questions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessQuestionBank() throws Exception {
        mockMvc.perform(get("/api/teacher/questions"))
                .andExpect(status().isForbidden());

        verify(questionBankService, never()).search(any(), any());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void searchBindsAllFiltersAndPaging() throws Exception {
        when(questionBankService.search(any(), eq(actor))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/teacher/questions")
                        .param("keyword", "capital")
                        .param("questionType", "SINGLE_CHOICE")
                        .param("status", "ACTIVE")
                        .param("tag", "geography")
                        .param("dictionaryId", "10")
                        .param("metaWordId", "20")
                        .param("creatorId", "8")
                        .param("page", "2")
                        .param("size", "15"))
                .andExpect(status().isOk());

        ArgumentCaptor<QuestionBankSearchRequest> captor = ArgumentCaptor.forClass(QuestionBankSearchRequest.class);
        verify(questionBankService).search(captor.capture(), eq(actor));
        QuestionBankSearchRequest request = captor.getValue();
        assertEquals("capital", request.getKeyword());
        assertEquals(QuestionType.SINGLE_CHOICE, request.getQuestionType());
        assertEquals(QuestionBankItemStatus.ACTIVE, request.getStatus());
        assertEquals("geography", request.getTag());
        assertEquals(10L, request.getDictionaryId());
        assertEquals(20L, request.getMetaWordId());
        assertEquals(8L, request.getCreatorId());
        assertEquals(2, request.getPage());
        assertEquals(15, request.getSize());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void createReturnsCreatedAndBindsValidatedBody() throws Exception {
        when(questionBankService.create(any(), eq(actor))).thenReturn(response(100L));

        mockMvc.perform(post("/api/teacher/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_JSON))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateQuestionRequest> captor = ArgumentCaptor.forClass(CreateQuestionRequest.class);
        verify(questionBankService).create(captor.capture(), eq(actor));
        assertEquals(QuestionBankItemStatus.DRAFT, captor.getValue().getStatus());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void invalidCreateBodyReturnsBadRequestBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/teacher/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionType": "FILL_IN_BLANK",
                                  "stem": " ",
                                  "acceptedAnswers": [],
                                  "defaultScore": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(questionBankService, never()).create(any(), any());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void updateRouteReturnsOk() throws Exception {
        when(questionBankService.update(eq(55L), any(), eq(actor))).thenReturn(response(55L));

        mockMvc.perform(put("/api/teacher/questions/{questionId}", 55L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_JSON))
                .andExpect(status().isOk());

        verify(questionBankService).update(eq(55L), any(UpdateQuestionRequest.class), eq(actor));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void copyRouteReturnsCreatedAndBindsStem() throws Exception {
        when(questionBankService.copy(eq(55L), any(), eq(actor))).thenReturn(response(100L));

        mockMvc.perform(post("/api/teacher/questions/{questionId}/copy", 55L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stem\":\"Copied stem\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<CopyQuestionRequest> captor = ArgumentCaptor.forClass(CopyQuestionRequest.class);
        verify(questionBankService).copy(eq(55L), captor.capture(), eq(actor));
        assertEquals("Copied stem", captor.getValue().getStem());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void copyRouteAcceptsMissingBody() throws Exception {
        when(questionBankService.copy(eq(55L), isNull(), eq(actor))).thenReturn(response(100L));

        mockMvc.perform(post("/api/teacher/questions/{questionId}/copy", 55L))
                .andExpect(status().isCreated());

        verify(questionBankService).copy(55L, null, actor);
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void archiveRouteReturnsNoContent() throws Exception {
        mockMvc.perform(patch("/api/teacher/questions/{questionId}/archive", 55L))
                .andExpect(status().isNoContent());

        verify(questionBankService).archive(55L, actor);
    }

    private QuestionBankItemResponse response(Long id) {
        QuestionBankItemResponse response = new QuestionBankItemResponse();
        response.setId(id);
        response.setStatus(QuestionBankItemStatus.DRAFT);
        return response;
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
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
