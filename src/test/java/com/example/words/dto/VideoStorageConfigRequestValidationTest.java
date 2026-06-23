package com.example.words.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.words.model.VideoStorageProviderType;
import com.example.words.model.VideoStorageConfigStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VideoStorageConfigRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void createRequestShouldAllowShortNonBlankValuesForDraftConfigs() {
        CreateVideoStorageConfigRequest request = new CreateVideoStorageConfigRequest(
                "A",
                "sid",
                "sk",
                "ap-guangzhou",
                null,
                null,
                VideoStorageProviderType.TENCENT_VOD,
                null,
                VideoStorageConfigStatus.DISABLED,
                Boolean.FALSE,
                "draft"
        );

        Set<ConstraintViolation<CreateVideoStorageConfigRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void createRequestShouldStillRejectBlankRequiredFields() {
        CreateVideoStorageConfigRequest request = new CreateVideoStorageConfigRequest(
                " ",
                " ",
                "",
                " ",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        Set<ConstraintViolation<CreateVideoStorageConfigRequest>> violations = validator.validate(request);

        assertEquals(7, violations.size());
    }

    @Test
    void createRequestShouldRejectVolcengineConfigWithoutSpaceName() {
        CreateVideoStorageConfigRequest request = new CreateVideoStorageConfigRequest(
                "火山云",
                "ak",
                "sk",
                "cn-north-1",
                123456L,
                null,
                VideoStorageProviderType.VOLCENGINE_VOD,
                " ",
                VideoStorageConfigStatus.ENABLED,
                Boolean.TRUE,
                null
        );

        Set<ConstraintViolation<CreateVideoStorageConfigRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("spaceName is required for Volcengine VOD", violations.iterator().next().getMessage());
    }
}
