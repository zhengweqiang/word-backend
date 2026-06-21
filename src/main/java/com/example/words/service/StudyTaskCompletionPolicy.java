package com.example.words.service;

import com.example.words.model.StudyRecordResult;

public final class StudyTaskCompletionPolicy {

    private StudyTaskCompletionPolicy() {
    }

    public static boolean completesTask(StudyRecordResult result) {
        return result == StudyRecordResult.CORRECT || result == StudyRecordResult.SKIPPED;
    }
}
