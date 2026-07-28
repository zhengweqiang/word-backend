package com.example.words.service;

import com.example.words.model.PaperRelease;
import com.example.words.model.PaperResultVisibility;

final class PaperResultVisibilityPolicy {

    private PaperResultVisibilityPolicy() {
    }

    static boolean isScoreVisible(PaperRelease release) {
        return release.getResultVisibility() != null
                && (release.getResultVisibility() != PaperResultVisibility.HIDDEN_UNTIL_RELEASED
                    || release.getResultsReleasedAt() != null);
    }
}
