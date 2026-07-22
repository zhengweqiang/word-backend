# Student Points MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the first student-points MVP across the Spring Boot backend, React student workspace, and SolidJS teacher/admin workspace.

**Architecture:** Existing business modules publish small, after-commit point-event requests into a deep student-points module. The module owns rule resolution, idempotency, account locking, ledger writes, retries, manual adjustments, reversal, audit, and role-specific queries. Event claiming, successful posting, and failed-attempt recording use separate transactions so a failed ledger transaction cannot erase its own failure record.

**Tech Stack:** Java 17, Spring Boot 3.1.8, Spring Data JPA, PostgreSQL, Flyway, JUnit 5, Mockito, React 19, SolidJS 1.9, TypeScript, Vitest, Node test runner.

---

## File Map

- `src/main/resources/db/migration/V31__create_student_point_tables.sql`: tables, constraints, indexes, rule seeds, historical student account backfill.
- `src/main/java/com/example/words/model/StudentPoint*.java`: persisted point entities and enums.
- `src/main/java/com/example/words/repository/StudentPoint*Repository.java`: point persistence, account locking, conditional event claiming, paged queries.
- `src/main/java/com/example/words/service/StudentPointAccountService.java`: account creation and invariant checks.
- `src/main/java/com/example/words/service/StudentPointEventPublisher.java`: after-commit bridge used by learning flows.
- `src/main/java/com/example/words/service/StudentPointEventService.java`: idempotent event creation and rule snapshots.
- `src/main/java/com/example/words/service/StudentPointLedgerService.java`: account mutation, immutable ledger, and reversal.
- `src/main/java/com/example/words/service/StudentPointEventProcessor.java`: claim/process orchestration.
- `src/main/java/com/example/words/service/StudentPointFailureRecorder.java`: independent failure transaction.
- `src/main/java/com/example/words/service/StudentPointRetryScheduler.java`: single-instance retry, startup recovery, and timeout recovery.
- `src/main/java/com/example/words/service/StudentPointAdjustmentService.java`: teacher/admin manual adjustment workflow.
- `src/main/java/com/example/words/service/StudentPointQueryService.java`: role-safe account, transaction, and event projections.
- `src/main/java/com/example/words/controller/StudentPointController.java`: current-student queries.
- `src/main/java/com/example/words/controller/TeacherPointController.java`: managed-student queries and adjustments.
- `src/main/java/com/example/words/controller/AdminPointController.java`: global queries, retries, cancellation, reversal.
- `src/main/java/com/example/words/dto/*Point*.java`: request and response contracts.
- `frontend/src/student/StudentPoints.tsx`: student balance and ledger page.
- `frontend/src/student/StudentWorkspace.tsx`: points navigation and page loading.
- `frontend/src/api/index.ts`, `frontend/src/types/index.ts`: student point client contracts.
- `frontend/admin/src/pages/points-page.tsx`: teacher/admin point operations page.
- `frontend/admin/src/lib/api.ts`, `frontend/admin/src/types/api.ts`: admin point client contracts.
- `frontend/admin/src/App.tsx`, `frontend/admin/src/components/layout/app-shell.tsx`: points route and navigation.

## Task 1: Database Model and Persistence

**Files:**
- Create: `src/main/resources/db/migration/V31__create_student_point_tables.sql`
- Create: `src/main/java/com/example/words/model/StudentPointAccount.java`
- Create: `src/main/java/com/example/words/model/StudentPointTransaction.java`
- Create: `src/main/java/com/example/words/model/StudentPointEvent.java`
- Create: `src/main/java/com/example/words/model/StudentPointEventAttempt.java`
- Create: `src/main/java/com/example/words/model/StudentPointRule.java`
- Create: `src/main/java/com/example/words/model/StudentPointAdjustmentRequest.java`
- Create: `src/main/java/com/example/words/model/StudentPointEnums.java` as separate enum files matching repository convention
- Create: `src/main/java/com/example/words/repository/StudentPoint*Repository.java`
- Test: `src/test/java/com/example/words/repository/StudentPointRepositoryIntegrationTest.java`

- [ ] **Step 1: Write a failing repository test**

```java
@DataJpaTest
class StudentPointRepositoryIntegrationTest {
    @Autowired private StudentPointAccountRepository accountRepository;

    @Test
    void enforcesOneAccountPerStudent() {
        accountRepository.saveAndFlush(StudentPointAccount.create(7L));
        assertThrows(DataIntegrityViolationException.class,
                () -> accountRepository.saveAndFlush(StudentPointAccount.create(7L)));
    }
}
```

- [ ] **Step 2: Run the test and verify it fails because point persistence does not exist**

Run: `./mvnw test -Dtest=StudentPointRepositoryIntegrationTest`

- [ ] **Step 3: Add entities, enums, repositories, and migration**

The migration must create all six MVP tables, use `ON DELETE RESTRICT` or no foreign key, keep `student_point_transactions` free of foreign keys, enforce unique `rule_code` and idempotency keys, and insert accounts for existing `users.role = 'STUDENT'`.

```sql
INSERT INTO student_point_accounts (student_id)
SELECT id FROM users WHERE role = 'STUDENT'
ON CONFLICT (student_id) DO NOTHING;
```

The event entity includes persistent processing metadata:

```java
private PointAttemptTriggerType processingTriggerType;
private Long processingOperatorId;
private String processingOperatorRole;
private String processingReason;
```

- [ ] **Step 4: Run the repository test and all existing repository tests**

Run: `./mvnw test -Dtest=StudentPointRepositoryIntegrationTest,DictionaryWordRepositoryIntegrationTest`

## Task 2: Account Creation With Student Creation

**Files:**
- Create: `src/main/java/com/example/words/service/StudentPointAccountService.java`
- Modify: `src/main/java/com/example/words/service/UserService.java`
- Test: `src/test/java/com/example/words/service/StudentPointAccountServiceTest.java`
- Modify: `src/test/java/com/example/words/service/UserServiceTest.java`

- [ ] **Step 1: Write failing tests for student-only account creation**

```java
@Test
void createsPointAccountWhenCreatingStudent() {
    UserResponse response = userService.createUser(studentRequest());
    verify(studentPointAccountService).createForStudent(response.getId());
}

@Test
void doesNotCreatePointAccountWhenCreatingTeacher() {
    userService.createUser(teacherRequest());
    verifyNoInteractions(studentPointAccountService);
}
```

- [ ] **Step 2: Run tests and verify the new constructor/behavior is missing**

Run: `./mvnw test -Dtest=UserServiceTest,StudentPointAccountServiceTest`

- [ ] **Step 3: Implement same-transaction account creation**

```java
AppUser savedUser = appUserRepository.save(user);
if (savedUser.getRole() == UserRole.STUDENT) {
    studentPointAccountService.createForStudent(savedUser.getId());
}
return UserResponse.from(savedUser);
```

- [ ] **Step 4: Re-run focused tests**

Run: `./mvnw test -Dtest=UserServiceTest,StudentPointAccountServiceTest`

## Task 3: Ledger Posting, Idempotency, Deduction, and Reversal

**Files:**
- Create: `src/main/java/com/example/words/service/StudentPointLedgerService.java`
- Create: `src/main/java/com/example/words/exception/StudentPointOperationException.java`
- Modify: `src/main/java/com/example/words/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/example/words/service/StudentPointLedgerServiceTest.java`

- [ ] **Step 1: Write failing behavior tests**

Cover earn, duplicate idempotency, insufficient deduction, exact reversal, duplicate reversal, reversal with insufficient balance, and immutable account/student snapshots.

```java
@Test
void rejectsReversalWhenCurrentBalanceCannotReturnFullAward() {
    when(accountRepository.findByStudentIdForUpdate(7L)).thenReturn(Optional.of(accountWithBalance(20)));
    when(transactionRepository.findById(100L)).thenReturn(Optional.of(earnedTransaction(100L, 7L, 100)));
    StudentPointOperationException error = assertThrows(StudentPointOperationException.class,
            () -> ledgerService.reverse(100L, adminActor(), "错误奖励"));
    assertEquals("INSUFFICIENT_POINTS_FOR_REVERSAL", error.getCode());
    verify(transactionRepository, never()).save(any());
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./mvnw test -Dtest=StudentPointLedgerServiceTest`

- [ ] **Step 3: Implement the ledger as the only account-mutating module**

```java
@Transactional
public StudentPointTransaction post(PointPosting posting) {
    return transactionRepository.findByIdempotencyKey(posting.idempotencyKey())
            .orElseGet(() -> postNew(posting));
}
```

Use `findByStudentIdForUpdate`, derive both `accountId` and `studentId` from the locked account, update lifetime totals consistently, and never accept an account ID from a controller request.

- [ ] **Step 4: Add stable point error codes to error responses and re-run tests**

Run: `./mvnw test -Dtest=StudentPointLedgerServiceTest,GlobalExceptionHandlerTest`

## Task 4: Events, Three-Transaction Processing, Attempts, and Retry

**Files:**
- Create: `src/main/java/com/example/words/service/StudentPointEventService.java`
- Create: `src/main/java/com/example/words/service/StudentPointEventProcessor.java`
- Create: `src/main/java/com/example/words/service/StudentPointPostingTransaction.java`
- Create: `src/main/java/com/example/words/service/StudentPointFailureRecorder.java`
- Create: `src/main/java/com/example/words/service/StudentPointRetryScheduler.java`
- Test: `src/test/java/com/example/words/service/StudentPointEventServiceTest.java`
- Test: `src/test/java/com/example/words/service/StudentPointEventProcessorTest.java`

- [ ] **Step 1: Write failing tests for event idempotency and rule snapshots**

```java
@Test
void duplicateEventReturnsExistingEvent() {
    when(eventRepository.findByIdempotencyKey("study-record:9:correct:STUDY_RECORD_CORRECT"))
            .thenReturn(Optional.of(existing));
    assertSame(existing, eventService.create(request));
    verify(eventRepository, never()).save(any());
}
```

- [ ] **Step 2: Write failing processor tests**

Verify conditional claim, success atomicity, automatic failure count, manual failure audit, maximum three automatic attempts, restart recovery, timeout recovery, and `PROCESSING` conflict.

- [ ] **Step 3: Run and verify RED**

Run: `./mvnw test -Dtest=StudentPointEventServiceTest,StudentPointEventProcessorTest`

- [ ] **Step 4: Implement the three transactions**

```java
public void process(long eventId, PointAttemptContext context) {
    if (!eventService.claim(eventId, context)) {
        throw StudentPointOperationException.conflict("POINT_EVENT_PROCESSING", "事件正在处理中");
    }
    try {
        postingTransaction.post(eventId, context);
    } catch (RuntimeException failure) {
        failureRecorder.recordFailure(eventId, context, failure);
    }
}
```

`StudentPointFailureRecorder#recordFailure` must live in a different Spring bean and use `@Transactional(propagation = Propagation.REQUIRES_NEW)`.

- [ ] **Step 5: Implement the single-instance scheduler and re-run tests**

Use one scheduled scanner for due `PENDING/FAILED` events, one startup recovery method, and one timeout recovery method. No distributed locks or multi-worker claiming design.

Run: `./mvnw test -Dtest=StudentPointEventServiceTest,StudentPointEventProcessorTest`

## Task 5: Manual Adjustment and Admin Recovery Workflows

**Files:**
- Create: `src/main/java/com/example/words/service/StudentPointAdjustmentService.java`
- Create: `src/main/java/com/example/words/service/StudentPointAdminService.java`
- Test: `src/test/java/com/example/words/service/StudentPointAdjustmentServiceTest.java`
- Test: `src/test/java/com/example/words/service/StudentPointAdminServiceTest.java`

- [ ] **Step 1: Write failing adjustment tests**

Cover teacher responsibility, non-zero amount, required reason, synchronous success, failed request state, blocked replacement during automatic retry, cancellation before replacement, and admin manual retry audit.

```java
@Test
void rejectsTeacherAdjustmentForUnmanagedStudent() {
    when(teacherStudentService.isTeacherResponsibleForStudent(5L, 7L)).thenReturn(false);
    assertThrows(AccessDeniedException.class,
            () -> adjustmentService.adjust(teacher(), requestFor(7L, 10, "课堂表现")));
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./mvnw test -Dtest=StudentPointAdjustmentServiceTest,StudentPointAdminServiceTest`

- [ ] **Step 3: Implement adjustment and admin workflows**

Create the request and `MANUAL_ADJUSTMENT` event, then process synchronously. On success, commit account, ledger, event, attempt, and request `APPLIED` together. Reversal updates the request to `REVERSED` in the same ledger transaction while leaving the original event `SUCCEEDED`.

- [ ] **Step 4: Re-run focused tests**

Run: `./mvnw test -Dtest=StudentPointAdjustmentServiceTest,StudentPointAdminServiceTest`

## Task 6: Learning Integration After Commit

**Files:**
- Create: `src/main/java/com/example/words/service/StudentPointEventPublisher.java`
- Modify: `src/main/java/com/example/words/service/StudyPlanService.java`
- Modify: `src/test/java/com/example/words/service/StudyPlanServiceTest.java`
- Test: `src/test/java/com/example/words/service/StudentPointEventPublisherTest.java`

- [ ] **Step 1: Write failing tests for correct-answer and first-completion events**

```java
verify(pointEventPublisher).publishAfterCommit(argThat(request ->
        request.ruleCode().equals("STUDY_RECORD_CORRECT")
                && request.sourceId().equals(savedStudyRecord.getId())));
verify(pointEventPublisher).publishAfterCommit(argThat(request ->
        request.ruleCode().equals("DAILY_TASK_COMPLETED")
                && request.sourceId().equals(studyDayTask.getId())));
```

Also verify an incorrect answer emits no correct-answer event and a previously completed task emits no second daily event.

- [ ] **Step 2: Run and verify RED**

Run: `./mvnw test -Dtest=StudyPlanServiceTest,StudentPointEventPublisherTest`

- [ ] **Step 3: Implement after-commit publication**

```java
if (TransactionSynchronizationManager.isActualTransactionActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { createSafely(request); }
    });
} else {
    createSafely(request);
}
```

Failures log `sourceType`, `sourceId`, `sourceKey`, and `ruleCode`, and never propagate into the completed learning request.

- [ ] **Step 4: Re-run learning and point tests**

Run: `./mvnw test -Dtest=StudyPlanServiceTest,StudentPointEventPublisherTest,StudentPointEventServiceTest`

## Task 7: Backend Query Interfaces and Controllers

**Files:**
- Create: `src/main/java/com/example/words/service/StudentPointQueryService.java`
- Create: `src/main/java/com/example/words/controller/StudentPointController.java`
- Create: `src/main/java/com/example/words/controller/TeacherPointController.java`
- Create: `src/main/java/com/example/words/controller/AdminPointController.java`
- Create: `src/main/java/com/example/words/dto/*Point*.java`
- Test: `src/test/java/com/example/words/controller/StudentPointControllerTest.java`
- Test: `src/test/java/com/example/words/controller/TeacherPointControllerTest.java`
- Test: `src/test/java/com/example/words/controller/AdminPointControllerTest.java`

- [ ] **Step 1: Write failing controller tests for role-safe routes**

Use MockMvc to verify current-student isolation, teacher responsibility checks, admin-only recovery actions, validation errors, pagination, and stable point error codes.

- [ ] **Step 2: Run and verify RED**

Run: `./mvnw test -Dtest=StudentPointControllerTest,TeacherPointControllerTest,AdminPointControllerTest`

- [ ] **Step 3: Implement DTOs and controllers**

The public routes are exactly:

```text
GET  /api/students/me/points
GET  /api/students/me/points/transactions
GET  /api/teachers/points/students
GET  /api/teachers/points/students/{studentId}/transactions
POST /api/teachers/points/adjustments
GET  /api/admin/points/accounts
GET  /api/admin/points/transactions
GET  /api/admin/points/events
POST /api/admin/points/transactions/{id}/reverse
POST /api/admin/points/events/{id}/retry
POST /api/admin/points/events/{id}/cancel
```

- [ ] **Step 4: Run focused and backend-wide tests**

Run: `./mvnw test`

## Task 8: Student React Points Page

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/index.ts`
- Create: `frontend/src/student/student-points-state.ts`
- Create: `frontend/src/student/StudentPoints.tsx`
- Modify: `frontend/src/student/StudentWorkspace.tsx`
- Modify: `frontend/src/student/student-workspace.css`
- Create: `frontend/tests/student-points.test.mjs`

- [ ] **Step 1: Write failing state tests**

```javascript
test('formats signed point amounts', () => {
  assert.equal(formatPointAmount(10), '+10');
  assert.equal(formatPointAmount(-5), '-5');
});
```

Also cover source labels, empty state, and pagination append behavior.

- [ ] **Step 2: Run and verify RED**

Run: `npm --prefix frontend test -- student-points.test.mjs`

- [ ] **Step 3: Implement types, client, page, and five-item navigation**

Replace the bottom `词库` item with `积分`; keep `StudentLibrary` reachable from the existing home shortcut. Use Phosphor icons, existing loading/error patterns, and a compact account header plus unframed ledger list.

- [ ] **Step 4: Run student tests and root frontend build**

Run: `npm --prefix frontend test`

Run: `npm --prefix frontend run build:root`

## Task 9: SolidJS Teacher/Admin Points Page

**Files:**
- Modify: `frontend/admin/src/types/api.ts`
- Modify: `frontend/admin/src/lib/api.ts`
- Create: `frontend/admin/src/pages/points-page.tsx`
- Create: `frontend/admin/src/pages/points-page.test.tsx`
- Modify: `frontend/admin/src/App.tsx`
- Modify: `frontend/admin/src/components/layout/app-shell.tsx`

- [ ] **Step 1: Write failing role-specific page tests**

```tsx
it("shows adjustment controls to teachers but hides event recovery", async () => {
    authState.role = "TEACHER";
    render(() => <PointsPage />);
    expect(await screen.findByText("人工调整")).toBeInTheDocument();
    expect(screen.queryByText("异常事件")).not.toBeInTheDocument();
});
```

Add admin tests for event retry/cancel reason validation and reversal confirmation.

- [ ] **Step 2: Run and verify RED**

Run: `npm --prefix frontend/admin test -- points-page.test.tsx`

- [ ] **Step 3: Implement route, navigation, page, dialogs, and feedback states**

Teachers see managed accounts and adjustments. Admins see account, transaction, and failed-event tabs with reason-required recovery actions. Use existing Solid UI primitives and Lucide icons.

- [ ] **Step 4: Run admin tests and build**

Run: `npm --prefix frontend/admin test`

Run: `npm --prefix frontend/admin run build`

## Task 10: Documentation, Regression, and Runtime Verification

**Files:**
- Modify: `docs/student-point-design.zh-CN.md`
- Verify: all files above

- [ ] **Step 1: Synchronize confirmed implementation details into the design document**

Document the three-transaction processor, processing metadata fields, after-commit learning integration, first-stage frontend scope, and selected navigation layout.

- [ ] **Step 2: Run all backend checks**

Run: `./mvnw test`

Run: `./mvnw checkstyle:check`

Run: `./mvnw verify`

- [ ] **Step 3: Run all frontend checks**

Run: `npm --prefix frontend test`

Run: `npm --prefix frontend run lint`

Run: `npm --prefix frontend run build`

- [ ] **Step 4: Rebuild the unified frontend container**

Run: `docker-compose up -d --build frontend`

- [ ] **Step 5: Smoke-test role routes and point workflows**

Verify student balance/history, teacher managed-student adjustment, admin failed-event recovery, reversal conflict, correct-answer award, and daily-task first-completion award.

