package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StudentPointProcessingPolicyTest {

    @Test
    void automaticRetryLimitShouldRemainExactlyThree() {
        assertEquals(3, StudentPointProcessingPolicy.MAX_AUTO_ATTEMPTS);
    }
}
