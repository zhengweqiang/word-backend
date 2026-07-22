# Task 6 Reliable Study Point Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make learning submissions idempotent and make point-event handoff nonblocking and recoverable without adding an outbox table.

**Architecture:** A global request key on `study_records` and a pessimistic lock on `student_study_plans` serialize replay for one plan, while the database unique constraint is the final global guard. Point creation is submitted after commit to one bounded worker; a bounded single-instance reconciliation scan recreates missing events from committed study records and completed tasks.

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA, PostgreSQL/Flyway, React 19, TypeScript, Node test runner.

---

### Task 1: Study request idempotency

**Files:**
- Create: `src/main/resources/db/migration/V33__add_study_record_request_idempotency.sql`
- Modify: `StudyRecord`, `RecordStudyRequest`, `StudentDashboardRecordRequest`, repositories and `StudyPlanService`
- Test: DTO, service, repository and transaction integration tests

- [ ] Write failing tests for validation, immutable persistence mapping, exact replay, conflict, generated IDs and concurrent replay.
- [ ] Run focused tests and confirm failures are caused by missing request-key behavior.
- [ ] Add V33, JPA mappings, row lock and replay comparison with a stable 409 conflict.
- [ ] Run focused backend tests until green.

### Task 2: Bounded asynchronous publication

**Files:**
- Modify: `StudentPointSchedulingConfig`, `StudentPointEventPublisher`
- Test: publisher unit and transaction integration tests

- [ ] Write failing tests proving after-commit submission is deferred, bounded-executor rejection is swallowed, rollback submits nothing, and queued failures do not affect the committed transaction.
- [ ] Add one named single-thread `ThreadPoolTaskExecutor` with a bounded queue and abort rejection policy.
- [ ] Submit `createSafely` from `afterCommit`; keep the no-transaction path immediate.
- [ ] Run publisher tests until green.

### Task 3: Missing-event reconciliation

**Files:**
- Create: `StudentPointReconciliationService`, `StudentPointReconciliationScheduler`
- Modify: `StudyRecordRepository`, `StudyDayTaskRepository`
- Test: repository and service/scheduler reconciliation tests

- [ ] Write failing tests for exact missing-source projections, existing-event exclusion, result/status filtering, batch limits and per-source isolation.
- [ ] Add ordered native `NOT EXISTS` projection queries with SQL `LIMIT`.
- [ ] Recreate the exact study-record and daily-task publish requests, one bounded page per source per run.
- [ ] Schedule periodic execution and one `ApplicationReadyEvent` run.
- [ ] Run reconciliation and focused point tests until green.

### Task 4: Frontend retry-key reuse

**Files:**
- Create: `frontend/src/student/study-submission-idempotency.ts`
- Modify: both React study submission components and shared payload types
- Test: `frontend/tests/study-submission-idempotency.test.mjs`

- [ ] Write failing helper tests for same-payload reuse, different result/item rotation and success clearing.
- [ ] Add a generic pending-submission helper that stores the complete payload and generated UUID.
- [ ] Wire both React flows to reuse a failed submission and clear it after success.
- [ ] Run root frontend tests and TypeScript/Vite build.

### Task 5: Final verification

- [ ] Run Task 6 backend tests and all focused student-point tests.
- [ ] Run root frontend tests and build.
- [ ] Rebuild the unified frontend Docker container.
- [ ] Run `git diff --check` and report exact totals without committing.
