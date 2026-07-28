package com.example.words.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.QuestionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void scoreMustFitNumericNineteenTwoColumn() {
        CreateQuestionRequest excessiveScale = request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"));
        excessiveScale.setDefaultScore(new BigDecimal("1.001"));
        CreateQuestionRequest excessiveIntegerDigits = request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"));
        excessiveIntegerDigits.setDefaultScore(new BigDecimal("100000000000000000.00"));
        CreateQuestionRequest maximumValue = request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"));
        maximumValue.setDefaultScore(new BigDecimal("99999999999999999.99"));

        assertViolation(excessiveScale, "defaultScore");
        assertViolation(excessiveIntegerDigits, "defaultScore");
        assertTrue(validator.validate(maximumValue).isEmpty());
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

    @Test
    void copyRequestOnlyAllowsStemOverride() {
        assertEquals(Set.of("stem"), java.util.Arrays.stream(CopyQuestionRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
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
