package com.example.words.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PaperAttemptPointSourcePostgresMigrationIntegrationTest {

    private static final List<String> PRIOR_SOURCE_TYPES = List.of(
            "STUDY_TASK",
            "STUDY_RECORD",
            "CLASSROOM_CHAT",
            "VIDEO_WATCH",
            "EXAM",
            "MANUAL_ADJUSTMENT",
            "ADMIN_CORRECTION",
            "REDEMPTION"
    );
    private static final List<String> EXAM_PAPER_MIGRATION_VERSIONS =
            List.of("39", "39.1", "39.2", "39.3");
    private static final String NEW_SOURCE_TYPE = "PAPER_RELEASE_ATTEMPT";
    private static final String INVALID_SOURCE_TYPE = "UNKNOWN_SOURCE";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Test
    void v40UpgradesTheRealMigrationChainWithoutLosingPriorPointSources() throws SQLException {
        flywayThrough("39.3").migrate();

        try (Connection connection = connection()) {
            assertEquals(EXAM_PAPER_MIGRATION_VERSIONS, successfulExamPaperMigrationVersions(connection));
            long studentId = insertStudent(connection);
            long accountId = insertPointAccount(connection, studentId);
            insertSourceRows(connection, studentId, accountId, PRIOR_SOURCE_TYPES, 1);
            assertSourceRows(connection, PRIOR_SOURCE_TYPES, 1);
        }

        flywayThrough("40").migrate();

        try (Connection connection = connection()) {
            assertEquals(
                    List.of("39", "39.1", "39.2", "39.3", "40"),
                    successfulExamPaperMigrationVersions(connection));
            assertSourceRows(connection, PRIOR_SOURCE_TYPES, 1);

            long studentId = findStudentId(connection);
            long accountId = findPointAccountId(connection, studentId);
            assertDoesNotThrow(() -> insertSourceRows(
                    connection, studentId, accountId, List.of(NEW_SOURCE_TYPE), 100));
            assertSourceRows(connection, List.of(NEW_SOURCE_TYPE), 1);

            assertCheckViolation(() -> insertTransaction(
                    connection, accountId, studentId, INVALID_SOURCE_TYPE, 200));
            assertCheckViolation(() -> insertEvent(
                    connection, studentId, INVALID_SOURCE_TYPE, 200));
            assertCheckViolation(() -> insertRule(connection, INVALID_SOURCE_TYPE, 200));
        }
    }

    private static Flyway flywayThrough(String targetVersion) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(targetVersion))
                .load();
    }

    private static void insertSourceRows(
            Connection connection,
            long studentId,
            long accountId,
            List<String> sourceTypes,
            int firstSequence
    ) throws SQLException {
        for (int index = 0; index < sourceTypes.size(); index++) {
            String sourceType = sourceTypes.get(index);
            int sequence = firstSequence + index;
            insertRule(connection, sourceType, sequence);
            insertEvent(connection, studentId, sourceType, sequence);
            insertTransaction(connection, accountId, studentId, sourceType, sequence);
        }
    }

    private static long insertStudent(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO users (username, password_hash, display_name, role, status)
                VALUES ('v40-migration-student', 'not-used', 'V40 Migration Student', 'STUDENT', 'ACTIVE')
                RETURNING id
                """)) {
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static long insertPointAccount(Connection connection, long studentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO student_point_accounts (student_id)
                VALUES (?)
                RETURNING id
                """)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static long findStudentId(Connection connection) throws SQLException {
        return queryLong(connection, """
                SELECT id FROM users WHERE username = 'v40-migration-student'
                """);
    }

    private static long findPointAccountId(Connection connection, long studentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM student_point_accounts WHERE student_id = ?
                """)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void insertTransaction(
            Connection connection,
            long accountId,
            long studentId,
            String sourceType,
            int sequence
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO student_point_transactions (
                    account_id, student_id, transaction_type, amount,
                    balance_before, balance_after, source_type, source_id,
                    source_key, rule_code, idempotency_key
                ) VALUES (?, ?, 'EARN', ?, 0, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, accountId);
            statement.setLong(2, studentId);
            statement.setBigDecimal(3, BigDecimal.ONE);
            statement.setBigDecimal(4, BigDecimal.ONE);
            statement.setString(5, sourceType);
            statement.setLong(6, sequence);
            statement.setString(7, sourceKey(sequence));
            statement.setString(8, ruleCode(sequence));
            statement.setString(9, "v40-transaction-" + sequence);
            statement.executeUpdate();
        }
    }

    private static void insertEvent(
            Connection connection,
            long studentId,
            String sourceType,
            int sequence
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO student_point_events (
                    student_id, source_type, source_id, source_key,
                    rule_code, rule_name, points, idempotency_key
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, studentId);
            statement.setString(2, sourceType);
            statement.setLong(3, sequence);
            statement.setString(4, sourceKey(sequence));
            statement.setString(5, ruleCode(sequence));
            statement.setString(6, "V40 rule " + sequence);
            statement.setBigDecimal(7, BigDecimal.ONE);
            statement.setString(8, "v40-event-" + sequence);
            statement.executeUpdate();
        }
    }

    private static void insertRule(Connection connection, String sourceType, int sequence) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO student_point_rules (code, name, source_type, base_points, enabled)
                VALUES (?, ?, ?, ?, FALSE)
                """)) {
            statement.setString(1, ruleCode(sequence));
            statement.setString(2, "V40 rule " + sequence);
            statement.setString(3, sourceType);
            statement.setBigDecimal(4, BigDecimal.ONE);
            statement.executeUpdate();
        }
    }

    private static String sourceKey(int sequence) {
        return "v40-source-" + sequence;
    }

    private static String ruleCode(int sequence) {
        return "V40_RULE_" + sequence;
    }

    private static void assertSourceRows(
            Connection connection,
            List<String> sourceTypes,
            int expectedCountPerTable
    ) throws SQLException {
        for (String sourceType : sourceTypes) {
            assertEquals(expectedCountPerTable, countBySource(connection, "student_point_transactions", sourceType));
            assertEquals(expectedCountPerTable, countBySource(connection, "student_point_events", sourceType));
            assertEquals(expectedCountPerTable, countBySource(connection, "student_point_rules", sourceType));
        }
    }

    private static int countBySource(Connection connection, String table, String sourceType) throws SQLException {
        String identityColumn = table.equals("student_point_rules") ? "code" : "idempotency_key";
        String identityPrefix = table.equals("student_point_rules") ? "V40_RULE_%" : "v40-%";
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE source_type = ? AND " + identityColumn + " LIKE ?")) {
            statement.setString(1, sourceType);
            statement.setString(2, identityPrefix);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static List<String> successfulExamPaperMigrationVersions(Connection connection) throws SQLException {
        List<String> versions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT version, success
                FROM flyway_schema_history
                WHERE version IN ('39', '39.1', '39.2', '39.3', '40')
                ORDER BY installed_rank
                """)) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    assertEquals(true, result.getBoolean("success"));
                    versions.add(result.getString("version"));
                }
            }
        }
        return versions;
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void assertCheckViolation(SqlOperation operation) {
        SQLException exception = assertThrows(SQLException.class, operation::execute);
        assertEquals("23514", exception.getSQLState());
    }

    @FunctionalInterface
    private interface SqlOperation {
        void execute() throws SQLException;
    }
}
