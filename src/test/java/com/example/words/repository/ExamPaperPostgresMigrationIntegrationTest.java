package com.example.words.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ExamPaperPostgresMigrationIntegrationTest {

    private static final String MIGRATION_PATH = "db/migration/V39__create_exam_paper_management.sql";
    private static final String SOFT_REMOVAL_MIGRATION_PATH =
            "db/migration/V39_2__soft_remove_paper_template_questions.sql";
    private static final String RELEASE_CORRECTION_MIGRATION_PATH =
            "db/migration/V39_3__add_paper_release_correction_audit.sql";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @BeforeAll
    static void applyMigration() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE dictionaries (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE meta_words (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE classrooms (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO users (id) VALUES (1), (2)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(MIGRATION_PATH));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(SOFT_REMOVAL_MIGRATION_PATH));
            statement.execute("INSERT INTO classrooms (id) VALUES (31)");
            statement.execute("INSERT INTO paper_templates (id, title, owner_user_id) "
                    + "VALUES (900, 'Migration trace', 1)");
            statement.execute("INSERT INTO paper_releases (id, paper_template_id, title, published_by_user_id) "
                    + "VALUES (900, 900, 'Migration trace release', 1)");
            statement.execute("INSERT INTO paper_release_targets "
                    + "(id, paper_release_id, student_id, source_classroom_id, targeted_by_user_id) "
                    + "VALUES (900, 900, 2, 31, 1)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(RELEASE_CORRECTION_MIGRATION_PATH));
        }
    }

    @Test
    void correctionMigrationBackfillsClassroomTraceAndRestrictsSupersedingActor() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try (var trace = statement.executeQuery("""
                    SELECT source_classroom_ids_json FROM paper_release_targets WHERE id = 900
                    """)) {
                trace.next();
                assertEquals("[31]", trace.getString(1));
            }
            assertForeignKeyViolation(() -> executeUpdate(connection, """
                    UPDATE paper_releases SET superseded_by_user_id = 999 WHERE id = 900
                    """));
        }
    }

    @Test
    void referencedTemplateQuestionCanBeSoftRemovedWithoutBreakingReleaseReference() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            executeUpdate(connection, "INSERT INTO paper_templates (id, title, owner_user_id) "
                    + "VALUES (30, 'Soft removal', 1)");
            executeUpdate(connection, """
                    INSERT INTO paper_template_questions (
                        id, paper_template_id, question_order, question_type,
                        stem, accepted_answers_json, score
                    ) VALUES (130, 30, 1, 'FILL_IN_BLANK', 'Template question', '["answer"]', 1.00)
                    """);
            executeUpdate(connection, """
                    INSERT INTO paper_releases (
                        id, paper_template_id, title, published_by_user_id
                    ) VALUES (30, 30, 'Release', 1)
                    """);
            executeUpdate(connection, """
                    INSERT INTO paper_release_questions (
                        id, paper_release_id, paper_template_question_id, question_order,
                        question_type, stem, accepted_answers_json, score
                    ) VALUES (230, 30, 130, 1, 'FILL_IN_BLANK', 'Frozen', '["answer"]', 1.00)
                    """);

            executeUpdate(connection, """
                    UPDATE paper_template_questions
                    SET removed_at = CURRENT_TIMESTAMP, question_order = -1
                    WHERE id = 130
                    """);

            try (var active = statement.executeQuery("""
                    SELECT COUNT(*) FROM paper_template_questions
                    WHERE paper_template_id = 30 AND removed_at IS NULL
                    """)) {
                active.next();
                assertEquals(0, active.getInt(1));
            }
            try (var reference = statement.executeQuery("""
                    SELECT paper_template_question_id FROM paper_release_questions WHERE id = 230
                    """)) {
                reference.next();
                assertEquals(130L, reference.getLong(1));
            }
        }
    }

    @Test
    void v39AllowsSameReleaseAnswersAndRejectsCrossReleaseAnswers() throws SQLException {
        try (Connection connection = connection()) {
            insertAttemptAndQuestion(connection, 10, 110, 210);
            insertAttemptAndQuestion(connection, 11, 111, 211);

            assertDoesNotThrow(() -> executeUpdate(connection, """
                    INSERT INTO student_paper_answers (
                        id, attempt_id, paper_release_id, release_question_id
                    ) VALUES (310, 210, 10, 110)
                    """));

            assertForeignKeyViolation(() -> executeUpdate(connection, """
                    INSERT INTO student_paper_answers (
                        id, attempt_id, paper_release_id, release_question_id
                    ) VALUES (311, 210, 10, 111)
                    """));
            assertForeignKeyViolation(() -> executeUpdate(connection, """
                    INSERT INTO student_paper_answers (
                        id, attempt_id, paper_release_id, release_question_id
                    ) VALUES (312, 210, 11, 111)
                    """));
        }
    }

    @Test
    void v39RestrictsDeletingAttemptsAndReleaseQuestionsReferencedByAnswers() throws SQLException {
        try (Connection connection = connection()) {
            insertAttemptAndQuestion(connection, 20, 120, 220);
            executeUpdate(connection, """
                    INSERT INTO student_paper_answers (
                        id, attempt_id, paper_release_id, release_question_id
                    ) VALUES (320, 220, 20, 120)
                    """);

            assertForeignKeyViolation(
                    () -> executeUpdate(connection, "DELETE FROM student_paper_attempts WHERE id = 220"));
            assertForeignKeyViolation(
                    () -> executeUpdate(connection, "DELETE FROM paper_release_questions WHERE id = 120"));
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void insertAttemptAndQuestion(
            Connection connection,
            long releaseId,
            long questionId,
            long attemptId
    ) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO paper_templates (id, title, owner_user_id)
                VALUES (%d, 'Template %d', 1)
                """.formatted(releaseId, releaseId));
        executeUpdate(connection, """
                INSERT INTO paper_releases (
                    id, paper_template_id, title, published_by_user_id
                ) VALUES (%d, %d, 'Release %d', 1)
                """.formatted(releaseId, releaseId, releaseId));
        executeUpdate(connection, """
                INSERT INTO paper_release_questions (
                    id, paper_release_id, question_order, question_type,
                    stem, accepted_answers_json, score
                ) VALUES (%d, %d, 1, 'SINGLE_CHOICE', 'Question', '["A"]', 1.00)
                """.formatted(questionId, releaseId));
        executeUpdate(connection, """
                INSERT INTO student_paper_attempts (id, paper_release_id, student_id)
                VALUES (%d, %d, 2)
                """.formatted(attemptId, releaseId));
    }

    private static int executeUpdate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private static void assertForeignKeyViolation(SqlOperation operation) {
        SQLException exception = assertThrows(SQLException.class, operation::execute);
        assertEquals("23503", exception.getSQLState());
    }

    @FunctionalInterface
    private interface SqlOperation {
        void execute() throws SQLException;
    }
}
