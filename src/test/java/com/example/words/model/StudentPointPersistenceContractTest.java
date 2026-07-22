package com.example.words.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Column;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class StudentPointPersistenceContractTest {

    private static final String MIGRATION_PATH = "db/migration/V31__create_student_point_tables.sql";
    private static final String ADJUSTMENT_IDEMPOTENCY_MIGRATION_PATH =
            "db/migration/V32__add_student_point_adjustment_idempotency.sql";
    private static final String STUDY_RECORD_IDEMPOTENCY_MIGRATION_PATH =
            "db/migration/V33__add_study_record_request_idempotency.sql";
    private static final String POINTS_ELIGIBILITY_MIGRATION_PATH =
            "db/migration/V34__add_study_points_eligibility.sql";
    private static final String RULE_AUDIT_MIGRATION_PATH =
            "db/migration/V35__add_student_point_rule_audits.sql";

    @Test
    void mapsAllMigrationRequiredTimestampsAsNonNullable() throws NoSuchFieldException {
        List<RequiredTimestamp> requiredTimestamps = List.of(
                new RequiredTimestamp(StudentPointAccount.class, "createdAt"),
                new RequiredTimestamp(StudentPointAccount.class, "updatedAt"),
                new RequiredTimestamp(StudentPointTransaction.class, "createdAt"),
                new RequiredTimestamp(StudentPointEvent.class, "createdAt"),
                new RequiredTimestamp(StudentPointEvent.class, "updatedAt"),
                new RequiredTimestamp(StudentPointEventAttempt.class, "startedAt"),
                new RequiredTimestamp(StudentPointRule.class, "createdAt"),
                new RequiredTimestamp(StudentPointRule.class, "updatedAt"),
                new RequiredTimestamp(StudentPointRuleAudit.class, "createdAt"),
                new RequiredTimestamp(StudentPointAdjustmentRequest.class, "createdAt")
        );

        for (RequiredTimestamp requiredTimestamp : requiredTimestamps) {
            Column column = requiredTimestamp.entityType()
                    .getDeclaredField(requiredTimestamp.fieldName())
                    .getAnnotation(Column.class);
            assertNotNull(column, requiredTimestamp + " must have @Column");
            assertFalse(column.nullable(), requiredTimestamp + " must map the migration NOT NULL constraint");
        }
    }

    @Test
    void migrationDefinesTheCriticalStudentPointContract() throws IOException {
        String migration = readMigration();
        List<String> tableNames = List.of(
                "student_point_accounts",
                "student_point_transactions",
                "student_point_events",
                "student_point_event_attempts",
                "student_point_rules",
                "student_point_adjustment_requests"
        );

        long createTableCount = Pattern.compile("(?im)^CREATE TABLE\\s+student_point_[a-z_]+\\s*\\(")
                .matcher(migration)
                .results()
                .count();
        assertEquals(6, createTableCount);
        for (String tableName : tableNames) {
            assertTrue(migration.contains("CREATE TABLE " + tableName + " ("), "missing table " + tableName);
        }

        long seedRowCount = Pattern.compile(
                        "(?m)^\\s*\\('(?:STUDY_RECORD_CORRECT|DAILY_TASK_COMPLETED)'"
                )
                .matcher(migration)
                .results()
                .count();
        assertEquals(2, seedRowCount);
        assertTrue(migration.contains("'STUDY_RECORD_CORRECT'"));
        assertTrue(migration.contains("'DAILY_TASK_COMPLETED'"));

        Pattern backfillPattern = Pattern.compile(
                "(?is)INSERT\\s+INTO\\s+student_point_accounts\\s*\\(student_id\\)\\s*"
                        + "SELECT\\s+id\\s+FROM\\s+users\\s+WHERE\\s+role\\s*=\\s*'STUDENT'\\s*"
                        + "ON\\s+CONFLICT\\s*\\(student_id\\)\\s+DO\\s+NOTHING"
        );
        assertTrue(backfillPattern.matcher(migration).find(), "missing existing-student account backfill");

        Matcher transactionTable = Pattern.compile(
                        "(?is)CREATE TABLE\\s+student_point_transactions\\s*\\((.*?)\\n\\);"
                )
                .matcher(migration);
        assertTrue(transactionTable.find(), "missing transaction table definition");
        assertFalse(transactionTable.group(1).toUpperCase().contains("REFERENCES"));
        assertFalse(migration.toUpperCase().contains("ON DELETE CASCADE"));
    }

    @Test
    void v32AddsImmutableRequestIdentityAndReplacementSnapshotsWithoutForeignKeys() throws Exception {
        String migration = readMigration(ADJUSTMENT_IDEMPOTENCY_MIGRATION_PATH);
        String normalized = migration.replaceAll("\\s+", " ").toUpperCase();

        assertTrue(normalized.contains("ADD COLUMN REQUEST_KEY VARCHAR(64)"));
        assertTrue(normalized.contains("UPDATE STUDENT_POINT_ADJUSTMENT_REQUESTS"));
        assertTrue(normalized.contains("'LEGACY:' || ID"));
        assertTrue(normalized.contains("ALTER COLUMN REQUEST_KEY SET NOT NULL"));
        assertTrue(normalized.contains("CONSTRAINT UK_STUDENT_POINT_ADJUSTMENTS_REQUEST_KEY UNIQUE (REQUEST_KEY)"));
        assertTrue(normalized.contains("ADD COLUMN REPLACES_REQUEST_ID BIGINT"));
        assertTrue(normalized.contains("ADD COLUMN REPLACED_BY_REQUEST_ID BIGINT"));
        assertFalse(normalized.contains("REFERENCES"));
        assertFalse(normalized.contains("ON DELETE"));

        Column requestKey = StudentPointAdjustmentRequest.class.getDeclaredField("requestKey")
                .getAnnotation(Column.class);
        assertNotNull(requestKey);
        assertFalse(requestKey.nullable());
        assertFalse(requestKey.updatable());
        assertEquals(64, requestKey.length());
        assertThrows(NoSuchMethodException.class,
                () -> StudentPointAdjustmentRequest.class.getMethod("setRequestKey", String.class));
    }

    @Test
    void v33AddsImmutableGlobalStudyRecordRequestKeyWithoutForeignKeys() throws Exception {
        String migration = readMigration(STUDY_RECORD_IDEMPOTENCY_MIGRATION_PATH);
        String normalized = migration.replaceAll("\\s+", " ").toUpperCase();

        assertTrue(normalized.contains("ADD COLUMN REQUEST_KEY VARCHAR(64)"));
        assertTrue(normalized.contains("UPDATE STUDY_RECORDS"));
        assertTrue(normalized.contains("'LEGACY:' || ID"));
        assertTrue(normalized.contains("ALTER COLUMN REQUEST_KEY SET NOT NULL"));
        assertTrue(normalized.contains("CONSTRAINT UK_STUDY_RECORDS_REQUEST_KEY UNIQUE (REQUEST_KEY)"));
        assertFalse(normalized.contains("REFERENCES"));
        assertFalse(normalized.contains("ON DELETE"));

        Column requestKey = StudyRecord.class.getDeclaredField("requestKey").getAnnotation(Column.class);
        assertNotNull(requestKey);
        assertFalse(requestKey.nullable());
        assertFalse(requestKey.updatable());
        assertEquals(64, requestKey.length());
        assertThrows(NoSuchMethodException.class, () -> StudyRecord.class.getMethod("setRequestKey", String.class));
    }

    @Test
    void v34KeepsHistoricalStudyRowsIneligibleWithoutForeignKeysOrCascades() throws Exception {
        String migration = readMigration(POINTS_ELIGIBILITY_MIGRATION_PATH);
        String normalized = migration.replaceAll("\\s+", " ").toUpperCase();

        assertTrue(normalized.contains(
                "ALTER TABLE STUDY_RECORDS ADD COLUMN POINTS_ELIGIBLE BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(normalized.contains(
                "ALTER TABLE STUDY_DAY_TASKS ADD COLUMN POINTS_ELIGIBLE BOOLEAN NOT NULL DEFAULT FALSE"));
        assertFalse(normalized.contains("UPDATE STUDY_RECORDS"));
        assertFalse(normalized.contains("UPDATE STUDY_DAY_TASKS"));
        assertFalse(normalized.contains("REFERENCES"));
        assertFalse(normalized.contains("ON DELETE"));

        Column recordEligibility = StudyRecord.class.getDeclaredField("pointsEligible").getAnnotation(Column.class);
        Column taskEligibility = StudyDayTask.class.getDeclaredField("pointsEligible").getAnnotation(Column.class);
        assertNotNull(recordEligibility);
        assertNotNull(taskEligibility);
        assertFalse(recordEligibility.nullable());
        assertFalse(taskEligibility.nullable());
    }

    @Test
    void v35CreatesPointScopedRuleAuditWithoutForeignKeysOrCascades() throws Exception {
        String migration = readMigration(RULE_AUDIT_MIGRATION_PATH);
        String normalized = migration.replaceAll("\\s+", " ").toUpperCase();

        assertTrue(normalized.contains("CREATE TABLE STUDENT_POINT_RULE_AUDITS"));
        assertTrue(normalized.contains("OPERATOR_ID BIGINT NOT NULL"));
        assertTrue(normalized.contains("REASON VARCHAR(500) NOT NULL"));
        assertTrue(normalized.contains("BEFORE_SNAPSHOT TEXT"));
        assertTrue(normalized.contains("AFTER_SNAPSHOT TEXT NOT NULL"));
        assertFalse(normalized.contains("REFERENCES"));
        assertFalse(normalized.contains("ON DELETE"));

        Column createdAt = StudentPointRuleAudit.class.getDeclaredField("createdAt").getAnnotation(Column.class);
        assertNotNull(createdAt);
        assertFalse(createdAt.nullable());
        assertFalse(createdAt.updatable());
    }

    private String readMigration() throws IOException {
        return readMigration(MIGRATION_PATH);
    }

    private String readMigration(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "migration must be available on the test classpath");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record RequiredTimestamp(Class<?> entityType, String fieldName) {
    }
}
