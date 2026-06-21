package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.words.model.StudyRecordResult;
import org.junit.jupiter.api.Test;

class StudyTaskCompletionPolicyTest {

    @Test
    void incorrectShouldKeepTaskPending() {
        assertFalse(StudyTaskCompletionPolicy.completesTask(StudyRecordResult.INCORRECT));
    }

    @Test
    void correctAndSkippedShouldCompleteTask() {
        assertTrue(StudyTaskCompletionPolicy.completesTask(StudyRecordResult.CORRECT));
        assertTrue(StudyTaskCompletionPolicy.completesTask(StudyRecordResult.SKIPPED));
    }
}
