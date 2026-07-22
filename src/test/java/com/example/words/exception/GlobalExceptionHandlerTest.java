package com.example.words.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void pointOperationErrorShouldExposeStableCodeAndPreserveResponseShape() throws Exception {
        mockMvc.perform(get("/test/point-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINTS_FOR_REVERSAL"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("积分余额不足，无法冲正"))
                .andExpect(jsonPath("$.details[0]").value("积分余额不足，无法冲正"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void legacyErrorShouldOmitAbsentCode() throws Exception {
        mockMvc.perform(get("/test/legacy-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("legacy bad request"));
    }

    @Test
    void invalidEnumQueryParameterShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/test/enum").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }

    @Test
    void invalidJsonBodyShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType("application/json")
                        .content("{\"status\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/point-error")
        void throwPointError() {
            throw new StudentPointOperationException(
                    "INSUFFICIENT_POINTS_FOR_REVERSAL",
                    HttpStatus.CONFLICT,
                    "积分余额不足，无法冲正"
            );
        }

        @GetMapping("/test/legacy-error")
        void throwLegacyError() {
            throw new BadRequestException("legacy bad request");
        }

        @GetMapping("/test/enum")
        void enumParameter(@RequestParam HttpStatus status) {
        }

        @PostMapping("/test/body")
        void body(@RequestBody StatusBody body) {
        }
    }

    record StatusBody(HttpStatus status) {
    }
}
