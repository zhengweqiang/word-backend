package com.example.words.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.words.model.AttentionState;
import com.example.words.model.StudyActionType;
import com.example.words.model.StudyRecordResult;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecordStudyRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldRequireRequestKey() {
        RecordStudyRequest request = request(" ");

        assertEquals(1, validator.validate(request).stream()
                .filter(violation -> violation.getPropertyPath().toString().equals("requestKey"))
                .count());
    }

    @Test
    void shouldLimitRequestKeyTo64Characters() {
        RecordStudyRequest request = request("x".repeat(65));

        assertEquals(1, validator.validate(request).stream()
                .filter(violation -> violation.getPropertyPath().toString().equals("requestKey"))
                .count());
    }

    @Test
    void dashboardConversionShouldPreserveRequestKey() {
        StudentDashboardRecordRequest request = new StudentDashboardRecordRequest(
                200L,
                3L,
                StudyActionType.LEARN,
                StudyRecordResult.CORRECT,
                20,
                18,
                2,
                4,
                AttentionState.FOCUSED,
                "request-123"
        );

        assertEquals("request-123", request.toRecordStudyRequest().getRequestKey());
    }

    private RecordStudyRequest request(String requestKey) {
        return new RecordStudyRequest(
                3L,
                StudyActionType.LEARN,
                StudyRecordResult.CORRECT,
                20,
                18,
                2,
                4,
                AttentionState.FOCUSED,
                requestKey
        );
    }
}
