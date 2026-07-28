package com.example.words.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PaperAttemptPointSourceMigrationContractTest {

    private static final String MIGRATION_PATH =
            "db/migration/V40__add_paper_attempt_point_source.sql";
    private static final List<String> SOURCE_TYPES = List.of(
            "STUDY_TASK",
            "STUDY_RECORD",
            "CLASSROOM_CHAT",
            "VIDEO_WATCH",
            "EXAM",
            "PAPER_RELEASE_ATTEMPT",
            "MANUAL_ADJUSTMENT",
            "ADMIN_CORRECTION",
            "REDEMPTION"
    );

    @Test
    void v40RecreatesExactlyTheThreePointSourceConstraintsWithEveryExistingSource() throws Exception {
        String sql = new ClassPathResource(MIGRATION_PATH)
                .getContentAsString(StandardCharsets.UTF_8);

        for (String table : List.of("transactions", "events", "rules")) {
            String constraint = "ck_student_point_" + table + "_source_type";
            assertTrue(sql.contains("DROP CONSTRAINT IF EXISTS " + constraint));
            assertTrue(sql.contains("ADD CONSTRAINT " + constraint));
        }
        for (String sourceType : SOURCE_TYPES) {
            assertEquals(3, occurrences(sql, "'" + sourceType + "'"));
        }
        assertFalse(sql.toUpperCase().contains("CASCADE"));
        assertFalse(sql.contains("INSERT INTO student_point_rules"));
    }

    private int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
