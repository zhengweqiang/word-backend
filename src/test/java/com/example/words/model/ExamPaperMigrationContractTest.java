package com.example.words.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;

class ExamPaperMigrationContractTest {

    private static final String MIGRATION_PATH = "db/migration/V39__create_exam_paper_management.sql";
    private static final String SOFT_REMOVAL_MIGRATION_PATH =
            "db/migration/V39_2__soft_remove_paper_template_questions.sql";

    @Test
    void v39BindsEachAnswerAttemptAndQuestionToTheSameReleaseWithRestrictiveForeignKeys() throws Exception {
        String migration = readMigration();
        String normalized = migration.replaceAll("\\s+", " ").toUpperCase();

        assertFalse(normalized.contains("ON DELETE CASCADE"));
        assertTrue(normalized.contains(
                "CONSTRAINT UK_STUDENT_PAPER_ATTEMPTS_ID_RELEASE UNIQUE (ID, PAPER_RELEASE_ID)"));
        assertTrue(normalized.contains(
                "CONSTRAINT UK_PAPER_RELEASE_QUESTIONS_ID_RELEASE UNIQUE (ID, PAPER_RELEASE_ID)"));
        assertTrue(normalized.contains("PAPER_RELEASE_ID BIGINT NOT NULL"));
        assertTrue(normalized.contains(
                "FOREIGN KEY (ATTEMPT_ID, PAPER_RELEASE_ID) REFERENCES "
                        + "STUDENT_PAPER_ATTEMPTS(ID, PAPER_RELEASE_ID) ON DELETE RESTRICT"));
        assertTrue(normalized.contains(
                "FOREIGN KEY (RELEASE_QUESTION_ID, PAPER_RELEASE_ID) REFERENCES "
                        + "PAPER_RELEASE_QUESTIONS(ID, PAPER_RELEASE_ID) ON DELETE RESTRICT"));

        Field paperReleaseId = StudentPaperAnswer.class.getDeclaredField("paperReleaseId");
        Column column = paperReleaseId.getAnnotation(Column.class);
        assertNotNull(column);
        assertEquals("paper_release_id", column.name());
        assertFalse(column.nullable());
        assertHasUniqueConstraint(StudentPaperAttempt.class, "id", "paper_release_id");
        assertHasUniqueConstraint(PaperReleaseQuestion.class, "id", "paper_release_id");
    }

    @Test
    void v39TwoAddsSoftRemovalWithoutWeakeningHistoricalReferences() throws Exception {
        String migration = readMigration(SOFT_REMOVAL_MIGRATION_PATH);
        String normalized = migration.replaceAll("\\s+", " ").toUpperCase();

        assertTrue(normalized.contains("ADD COLUMN REMOVED_AT TIMESTAMP"));
        assertTrue(normalized.contains("REMOVED_AT IS NULL AND QUESTION_ORDER > 0"));
        assertTrue(normalized.contains("REMOVED_AT IS NOT NULL AND QUESTION_ORDER < 0"));
        assertFalse(normalized.contains("DROP CONSTRAINT FK_PAPER_RELEASE_QUESTIONS_TEMPLATE_QUESTION"));

        Field removedAt = PaperTemplateQuestion.class.getDeclaredField("removedAt");
        Column column = removedAt.getAnnotation(Column.class);
        assertNotNull(column);
        assertEquals("removed_at", column.name());
    }

    private String readMigration() throws IOException {
        return readMigration(MIGRATION_PATH);
    }

    private String readMigration(String migrationPath) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(migrationPath)) {
            assertNotNull(input, "migration must be available on the test classpath");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void assertHasUniqueConstraint(Class<?> entityType, String... columnNames) {
        Table table = entityType.getAnnotation(Table.class);
        assertNotNull(table);
        for (UniqueConstraint uniqueConstraint : table.uniqueConstraints()) {
            if (java.util.Arrays.equals(columnNames, uniqueConstraint.columnNames())) {
                return;
            }
        }
        throw new AssertionError(entityType.getSimpleName() + " must declare unique columns "
                + java.util.Arrays.toString(columnNames));
    }
}
