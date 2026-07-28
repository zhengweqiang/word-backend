package com.example.words.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.QuestionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class QuestionBankRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validQuestionTypesPassStructuralValidation() {
        assertTrue(validator.validate(request(
                QuestionType.SINGLE_CHOICE, Map.of("A", "one", "B", "two"), List.of("A"))).isEmpty());
        assertTrue(validator.validate(request(
                QuestionType.MULTIPLE_CHOICE, Map.of("A", "one", "B", "two"), List.of("A", "B"))).isEmpty());
        assertTrue(validator.validate(request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"))).isEmpty());
    }

    @Test
    void stemIsRequired() {
        CreateQuestionRequest request = request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"));
        request.setStem(" ");

        assertViolation(request, "stem");
    }

    @Test
    void acceptedAnswersAreRequired() {
        CreateQuestionRequest request = request(
                QuestionType.SINGLE_CHOICE, Map.of("A", "one", "B", "two"), List.of());

        assertViolation(request, "acceptedAnswers");
    }

    @Test
    void acceptedAnswersCannotContainBlankValues() {
        CreateQuestionRequest request = request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of(" "));

        assertViolation(request, "acceptedAnswers[0]");
    }

    @Test
    void scoreMustBePositiveDecimal() {
        CreateQuestionRequest zeroScore = request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"));
        zeroScore.setDefaultScore(BigDecimal.ZERO);
        CreateQuestionRequest negativeScore = request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"));
        negativeScore.setDefaultScore(new BigDecimal("-0.01"));

        assertViolation(zeroScore, "defaultScore");
        assertViolation(negativeScore, "defaultScore");
    }

    @Test
    void noQuestionCanDeclareMoreThanFourOptions() {
        CreateQuestionRequest request = request(
                QuestionType.SINGLE_CHOICE,
                Map.of("A", "one", "B", "two", "C", "three", "D", "four", "E", "five"),
                List.of("A"));

        assertViolation(request, "options");
    }

    @Test
    void updateRequestUsesTheSameRequiredFieldValidation() {
        UpdateQuestionRequest request = new UpdateQuestionRequest(
                QuestionType.MULTIPLE_CHOICE,
                " ",
                Map.of("A", "one", "B", "two"),
                List.of(),
                BigDecimal.ZERO,
                null,
                List.of(),
                null,
                null,
                null,
                QuestionBankItemStatus.ACTIVE);

        Set<ConstraintViolation<UpdateQuestionRequest>> violations = validator.validate(request);

        assertEquals(Set.of("stem", "acceptedAnswers", "defaultScore"),
                violations.stream()
                        .map(violation -> violation.getPropertyPath().toString())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private CreateQuestionRequest request(
            QuestionType type, Map<String, String> options, List<String> acceptedAnswers) {
        return new CreateQuestionRequest(
                type,
                "What is correct?",
                options,
                acceptedAnswers,
                new BigDecimal("1.50"),
                null,
                List.of("vocabulary"),
                null,
                null,
                null,
                null);
    }

    private <T> void assertViolation(T request, String propertyPath) {
        assertTrue(validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.startsWith(propertyPath)));
    }
}
