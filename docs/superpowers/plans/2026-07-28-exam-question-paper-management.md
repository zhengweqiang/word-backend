# Exam Question Paper Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first-phase teacher-authored question bank, reusable paper, paper release, student attempt, auto-grading, and teacher result-review workflow described in `docs/requirements/exam-question-paper-requirements.zh-CN.md`.

**Architecture:** Add a new paper-assessment module beside the legacy dictionary-generated `Exam` flow instead of overloading `exams` and `exam_questions`. The new module owns immutable snapshots at two boundaries: paper-template question snapshots when building papers, and release question snapshots when publishing to students. Access control reuses existing authenticated user roles, `TeacherStudentService`, and classroom membership rules while storing release ownership for historical review.

**Tech Stack:** Java 17, Spring Boot 3.1.8, Spring Data JPA, PostgreSQL, Flyway, OpenCSV, JUnit 5, Mockito, H2 tests, React/SolidJS frontend under the unified `frontend` container.

## Global Constraints

- Keep legacy dictionary-generated exams working through existing `ExamService`, `ExamController`, `exams`, and `exam_questions`.
- Do not use cascading deletes. New foreign keys must use restrictive behavior or omit `ON DELETE CASCADE`.
- First phase supports only `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, and `FILL_IN_BLANK`.
- First phase import supports CSV only.
- A teacher can publish only to students or classes they are currently allowed to manage.
- Publishing to a class freezes the current eligible student list at publish time.
- Students who join a class after publication do not automatically receive the existing release.
- Students who leave a class after publication keep already-assigned attempts unless the release or attempt is explicitly invalidated.
- Deadline expiry does not block submission. Unsubmitted attempts become `OVERDUE`; later final submission becomes `SUBMITTED_LATE`.
- `SUBMITTED` means final submission before deadline. `SUBMITTED_LATE` means final submission after deadline.
- After any final submission exists, release content, submitted answers, and locked scores are not edited, deleted, or recalculated in place.
- Default blank-answer policy is `ALLOW_BLANK`.
- Default result visibility is `SCORE_ONLY`.
- Frontend changes under `frontend/` require rebuilding and restarting the unified `frontend` container before considering the change complete.

---

## File Map

### Backend Persistence

- Create `src/main/resources/db/migration/V39__create_exam_paper_management.sql`: new tables, enum-compatible string columns, constraints, indexes.
- Create `src/main/java/com/example/words/model/QuestionBankItem.java`: reusable question-bank item.
- Create `src/main/java/com/example/words/model/QuestionBankItemStatus.java`: `DRAFT`, `ACTIVE`, `ARCHIVED`.
- Create `src/main/java/com/example/words/model/QuestionType.java`: `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `FILL_IN_BLANK`.
- Create `src/main/java/com/example/words/model/PaperTemplate.java`: reusable paper template.
- Create `src/main/java/com/example/words/model/PaperTemplateStatus.java`: `DRAFT`, `READY`, `ARCHIVED`.
- Create `src/main/java/com/example/words/model/PaperTemplateQuestion.java`: paper-level question snapshot.
- Create `src/main/java/com/example/words/model/PaperRelease.java`: immutable publication metadata.
- Create `src/main/java/com/example/words/model/PaperReleaseStatus.java`: `SCHEDULED`, `OPEN`, `WITHDRAWN`, `INVALIDATED`, `SUPERSEDED`.
- Create `src/main/java/com/example/words/model/PaperReleaseQuestion.java`: release-level question snapshot.
- Create `src/main/java/com/example/words/model/PaperReleaseTarget.java`: frozen release target students and optional source classroom.
- Create `src/main/java/com/example/words/model/StudentPaperAttempt.java`: per-student attempt lifecycle and score.
- Create `src/main/java/com/example/words/model/StudentPaperAttemptStatus.java`: `NOT_STARTED`, `IN_PROGRESS`, `SUBMITTED`, `OVERDUE`, `SUBMITTED_LATE`, `INVALIDATED`.
- Create `src/main/java/com/example/words/model/StudentPaperAnswer.java`: per-question draft/final answer and grading result.
- Create `src/main/java/com/example/words/model/PaperBlankAnswerPolicy.java`: `ALLOW_BLANK`, `REQUIRE_ALL_ANSWERED`.
- Create `src/main/java/com/example/words/model/PaperResultVisibility.java`: `HIDDEN_UNTIL_RELEASED`, `SCORE_ONLY`, `SCORE_AND_ANSWERS`.
- Create `src/main/java/com/example/words/repository/QuestionBankItemRepository.java`.
- Create `src/main/java/com/example/words/repository/PaperTemplateRepository.java`.
- Create `src/main/java/com/example/words/repository/PaperTemplateQuestionRepository.java`.
- Create `src/main/java/com/example/words/repository/PaperReleaseRepository.java`.
- Create `src/main/java/com/example/words/repository/PaperReleaseQuestionRepository.java`.
- Create `src/main/java/com/example/words/repository/PaperReleaseTargetRepository.java`.
- Create `src/main/java/com/example/words/repository/StudentPaperAttemptRepository.java`.
- Create `src/main/java/com/example/words/repository/StudentPaperAnswerRepository.java`.
- Create `src/main/java/com/example/words/repository/QuestionImportBatchRepository.java`.
- Create `src/main/java/com/example/words/repository/QuestionImportPreviewRowRepository.java`.

### Backend Services And Controllers

- Create `src/main/java/com/example/words/service/ExamPaperAccessService.java`: question, paper, release, attempt permission helpers.
- Create `src/main/java/com/example/words/service/QuestionBankService.java`: manual question management, copy, archive, search.
- Create `src/main/java/com/example/words/service/QuestionImportService.java`: CSV preview and confirm flow.
- Create `src/main/java/com/example/words/service/PaperTemplateService.java`: paper draft editing, question snapshots, copy, preview.
- Create `src/main/java/com/example/words/service/PaperReleaseService.java`: publish, withdraw, invalidate, supersede, target freeze.
- Create `src/main/java/com/example/words/service/StudentPaperAttemptService.java`: assigned-paper list, open, save draft, submit, grading.
- Create `src/main/java/com/example/words/service/PaperResultReviewService.java`: teacher result summary, per-student details, per-question statistics, result visibility release.
- Create controllers:
  - `src/main/java/com/example/words/controller/TeacherQuestionBankController.java`
  - `src/main/java/com/example/words/controller/TeacherQuestionImportController.java`
  - `src/main/java/com/example/words/controller/TeacherPaperTemplateController.java`
  - `src/main/java/com/example/words/controller/TeacherPaperReleaseController.java`
  - `src/main/java/com/example/words/controller/StudentAssignedPaperController.java`

### DTOs

- Create request DTOs for question create/update/search, import preview/confirm, paper create/update/question reorder, release publish/correction, draft save, submit, result visibility.
- Create response DTOs for question list/detail, import preview row, paper preview/detail, release summary/detail, student assigned paper, attempt detail, submit result, teacher result overview, student result detail, per-question statistics.

### Frontend

- Modify `frontend/admin/src/lib/api.ts` and `frontend/admin/src/types/api.ts`: teacher/admin API clients and types.
- Add admin/teacher pages under `frontend/admin/src/pages/` for question bank, import, paper editor, release management, and result review.
- Modify admin routes/navigation in `frontend/admin/src/App.tsx`, `frontend/admin/src/components/layout/app-shell.tsx`, and `frontend/admin/src/components/layout/navigation.ts`.
- Modify `frontend/src/api/index.ts` and `frontend/src/types/index.ts`: student assigned-paper API clients and types.
- Modify student workspace routing/navigation in `frontend/src/student/StudentWorkspace.tsx`, `frontend/src/student/student-workspace-state.ts`, and `frontend/src/App.tsx`.
- Add student pages under `frontend/src/student/` for pending papers, answer page, and result page.
- Update `frontend/admin/src/index.css`, `frontend/src/App.css`, and `frontend/src/student/student-workspace.css`, keeping interfaces dense and task-focused.

### Tests

- Backend service tests under `src/test/java/com/example/words/service/`.
- Backend repository/integration tests under `src/test/java/com/example/words/repository/`.
- DTO validation tests under `src/test/java/com/example/words/dto/`.
- Existing regression tests: `ExamServiceTest`, `StudentDashboardServiceTest`, classroom and teacher-student tests.
- Frontend build verification through `npm run build` in `frontend/`.

---

## Data Model Contract

Use these table names:

- `question_bank_items`
- `paper_templates`
- `paper_template_questions`
- `paper_releases`
- `paper_release_questions`
- `paper_release_targets`
- `student_paper_attempts`
- `student_paper_answers`
- `question_import_batches`
- `question_import_preview_rows`

Minimum important constraints:

- `paper_template_questions.paper_template_id + question_order` unique.
- `paper_release_questions.paper_release_id + question_order` unique.
- `paper_release_targets.paper_release_id + student_id` unique.
- `student_paper_attempts.paper_release_id + student_id` unique.
- `student_paper_answers.attempt_id + release_question_id` unique.
- `question_import_batches.imported_by_user_id` and `question_import_preview_rows.batch_id` indexed.
- Release and attempt records store timestamps and reason fields for withdrawal, invalidation, supersession, and result release.

Snapshot columns should use `TEXT` or JSON-friendly `TEXT` payloads for options and accepted answers in the first phase. Recommended format:

- `options_json`: JSON array of objects `{ "key": "A", "text": "..." }`.
- `accepted_answers_json`: JSON array of normalized accepted answer strings.
- `selected_answers_json`: JSON array for single-choice and multiple-choice selected option keys.
- `blank_answers_json`: JSON array for fill-in-the-blank submitted text values.

---

### Task 1: Persistence Foundation

**Files:**
- Create: `src/main/resources/db/migration/V39__create_exam_paper_management.sql`
- Create: all model enum files listed in File Map.
- Create: all entity files listed in File Map.
- Create: all repository files listed in File Map.
- Test: `src/test/java/com/example/words/repository/ExamPaperPersistenceIntegrationTest.java`

**Interfaces:**
- Produces: all new entity and enum types.
- Produces: repository access needed by later services.
- Consumes: existing `AppUser`, `Dictionary`, `MetaWord`, `Classroom`, and user IDs by reference only.

- [x] Write `ExamPaperPersistenceIntegrationTest` with cases for unique attempts per release/student, unique release target per release/student, restrictive no-cascade expectations, decimal score persistence, and JSON snapshot persistence.
- [ ] Run `mvn test -Dtest=ExamPaperPersistenceIntegrationTest` and verify it fails because the new tables/entities do not exist.
- [x] Add Flyway migration `V39__create_exam_paper_management.sql`.
- [x] Add entities using Lombok annotations and explicit imports.
- [x] Add repositories extending `JpaRepository`; add `JpaSpecificationExecutor` for question and paper search repositories.
- [x] Run `mvn test -Dtest=ExamPaperPersistenceIntegrationTest`.
- [x] Run `mvn test -Dtest=ExamPaperPersistenceIntegrationTest,StudentPointPersistenceContractTest,DictionaryWordRepositoryIntegrationTest`.
- [x] Commit with `git commit -m "feat: add exam paper persistence model"`.

### Task 2: Shared Permission And Snapshot Helpers

**Files:**
- Create: `src/main/java/com/example/words/service/ExamPaperAccessService.java`
- Create: `src/main/java/com/example/words/service/ExamPaperSnapshotService.java`
- Create: `src/main/java/com/example/words/service/ExamPaperAnswerNormalizer.java`
- Test: `src/test/java/com/example/words/service/ExamPaperAccessServiceTest.java`
- Test: `src/test/java/com/example/words/service/ExamPaperAnswerNormalizerTest.java`

**Interfaces:**
- Produces: `ensureCanManageQuestion(AppUser actor, QuestionBankItem question)`.
- Produces: `ensureCanUseQuestion(AppUser actor, QuestionBankItem question)`.
- Produces: `ensureCanManagePaper(AppUser actor, PaperTemplate paper)`.
- Produces: `ensureCanPublishToStudent(AppUser actor, Long studentId)`.
- Produces: `ensureCanPublishToClassroom(AppUser actor, Long classroomId)`.
- Produces: `ensureCanReviewRelease(AppUser actor, PaperRelease release, Long studentId)`.
- Produces: `normalizeOptionKeys(Collection<String> values)`.
- Produces: `normalizeBlankAnswer(String value)`.

- [x] Write failing tests proving admins can audit, creating teachers can manage own assets, other teachers can use but not edit shared active questions, unrelated teachers cannot review attempts, publishing teacher can always review own release, and current responsible teacher can review current students.
- [x] Write failing normalizer tests for option key casing, duplicate option keys, blank trimming, and case-insensitive English blank answer comparison.
- [x] Run `mvn test -Dtest=ExamPaperAccessServiceTest,ExamPaperAnswerNormalizerTest` and verify RED.
- [x] Implement access checks using `TeacherStudentService`, `ClassroomService`, `ClassroomRepository`, and `ClassroomMemberRepository` where needed.
- [x] Implement answer normalization helpers used by import, grading, and draft/submit validation.
- [x] Run focused tests and then `mvn test -Dtest=ExamPaperAccessServiceTest,ExamPaperAnswerNormalizerTest,TeacherStudentServiceTest,ClassroomServiceTest`.
- [x] Commit with `git commit -m "feat: add exam paper permission helpers"`.

### Task 3: Question Bank Manual Management

**Files:**
- Create: `src/main/java/com/example/words/service/QuestionBankService.java`
- Create: `src/main/java/com/example/words/controller/TeacherQuestionBankController.java`
- Create DTOs:
  - `CreateQuestionRequest`
  - `UpdateQuestionRequest`
  - `QuestionBankSearchRequest`
  - `QuestionBankItemResponse`
  - `CopyQuestionRequest`
- Test: `src/test/java/com/example/words/service/QuestionBankServiceTest.java`
- Test: `src/test/java/com/example/words/dto/QuestionBankRequestValidationTest.java`

**Interfaces:**
- Consumes: `ExamPaperAccessService`, `ExamPaperAnswerNormalizer`.
- Produces: `QuestionBankItemResponse create(CreateQuestionRequest request, AppUser actor)`.
- Produces: `QuestionBankItemResponse update(Long questionId, UpdateQuestionRequest request, AppUser actor)`.
- Produces: `QuestionBankItemResponse copy(Long questionId, CopyQuestionRequest request, AppUser actor)`.
- Produces: `void archive(Long questionId, AppUser actor)`.
- Produces: paged search for active shared questions.

- [x] Write validation tests for single-choice, multiple-choice, fill-in-the-blank, positive decimal score, required stem, required accepted answers, and option count rules.
- [x] Write service tests for creating draft/active questions, editing own question, rejecting edit to another teacher's question, copying another teacher's question, archiving own question, admin archive, student denial, dictionary/word association trace fields, and search filters.
- [x] Run `mvn test -Dtest=QuestionBankRequestValidationTest,QuestionBankServiceTest` and verify RED.
- [x] Implement DTO validation annotations and service-level type-specific validation.
- [x] Implement controller under `/api/teacher/questions` with `@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")`.
- [x] Run focused tests.
- [x] Run `mvn test -Dtest=QuestionBankRequestValidationTest,QuestionBankServiceTest,GlobalExceptionHandlerTest`.
- [x] Commit with `git commit -m "feat: add teacher question bank management"`.

### Task 4: CSV Question Import Preview And Confirm

**Files:**
- Create: `src/main/java/com/example/words/service/QuestionImportService.java`
- Create: `src/main/java/com/example/words/controller/TeacherQuestionImportController.java`
- Create DTOs:
  - `QuestionImportPreviewResponse`
  - `QuestionImportPreviewRowResponse`
  - `ConfirmQuestionImportRequest`
  - `QuestionImportConfirmResponse`
- Test: `src/test/java/com/example/words/service/QuestionImportServiceTest.java`

**Interfaces:**
- Consumes: `QuestionBankService` validation rules.
- Produces: `QuestionImportPreviewResponse preview(MultipartFile file, AppUser actor)`.
- Produces: `QuestionImportConfirmResponse confirm(Long batchId, ConfirmQuestionImportRequest request, AppUser actor)`.

- [x] Write failing tests for valid CSV preview, missing required columns, invalid option/correct answer format, duplicate candidate detection, unresolved dictionary/word warnings, preview batch ownership, and confirming only selected valid rows.
- [x] Run `mvn test -Dtest=QuestionImportServiceTest` and verify RED.
- [x] Implement OpenCSV parsing with stable headers: `questionType`, `stem`, `optionA`, `optionB`, `optionC`, `optionD`, `correctAnswers`, `score`, `difficulty`, `tags`, `explanation`, `dictionaryName`, `word`.
- [x] Persist preview batches and preview rows so confirmation uses the reviewed data, not a re-parsed upload.
- [x] Implement duplicate candidate comparison using normalized stem, question type, options, and correct answers.
- [x] Implement controller endpoints under `/api/teacher/question-imports`.
- [x] Run `mvn test -Dtest=QuestionImportServiceTest,QuestionBankServiceTest`.
- [x] Commit with `git commit -m "feat: add question CSV import preview"`.

### Task 5: Paper Template Draft Editing

**Files:**
- Create: `src/main/java/com/example/words/service/PaperTemplateService.java`
- Create: `src/main/java/com/example/words/controller/TeacherPaperTemplateController.java`
- Create DTOs:
  - `CreatePaperTemplateRequest`
  - `UpdatePaperTemplateRequest`
  - `AddPaperQuestionRequest`
  - `ReorderPaperQuestionsRequest`
  - `UpdatePaperQuestionScoreRequest`
  - `PaperTemplateResponse`
  - `PaperTemplateQuestionResponse`
  - `CopyPaperTemplateRequest`
- Test: `src/test/java/com/example/words/service/PaperTemplateServiceTest.java`

**Interfaces:**
- Consumes: `QuestionBankService` and `ExamPaperSnapshotService`.
- Produces: paper draft create/update/archive/copy/search.
- Produces: paper-level question snapshots independent from later question-bank edits.
- Produces: paper total score calculation as decimal points.

- [x] Write failing tests for creating draft paper, adding active question snapshot, rejecting archived question, preserving snapshot after source edit, reordering questions, changing paper-specific score, total score recalculation, preview, copying another teacher's paper, rejecting direct edit to another teacher's paper, and archiving own paper.
- [x] Run `mvn test -Dtest=PaperTemplateServiceTest` and verify RED.
- [x] Implement paper create/update/search/copy/archive.
- [x] Implement add question by copying stem, type, options, accepted answers, explanation, source question ID, question order, and paper-specific score.
- [x] Implement reorder with contiguous question orders starting at 1.
- [x] Implement controller under `/api/teacher/papers`.
- [x] Run `mvn test -Dtest=PaperTemplateServiceTest,QuestionBankServiceTest`.
- [x] Commit with `git commit -m "feat: add paper template editing"`.

### Task 6: Publish Paper Releases

**Files:**
- Create: `src/main/java/com/example/words/service/PaperReleaseService.java`
- Create: `src/main/java/com/example/words/controller/TeacherPaperReleaseController.java`
- Create DTOs:
  - `PublishPaperRequest`
  - `PaperReleaseResponse`
  - `PaperReleaseTargetResponse`
  - `WithdrawPaperReleaseRequest`
  - `InvalidatePaperReleaseRequest`
  - `SupersedePaperReleaseRequest`
- Test: `src/test/java/com/example/words/service/PaperReleaseServiceTest.java`

**Interfaces:**
- Consumes: `PaperTemplateService`, `ExamPaperAccessService`, `ClassroomService`, `ClassroomMemberRepository`.
- Produces: immutable paper release snapshot.
- Produces: frozen target student list and one `StudentPaperAttempt` per target.
- Produces: withdrawal, invalidation, and supersession rules.

- [x] Write failing tests for publishing to one student, multiple students, a class, duplicate target de-duplication, unauthorized student rejection, archived classroom rejection, scheduled release, blank-answer policy default, result visibility default, release snapshot preservation after paper edit, and class membership changes after publication.
- [x] Write failing tests for withdrawal before final submission, rejecting withdrawal after final submission, invalidation with reason, superseding for the full original frozen target list, and preserving original submitted attempts.
- [x] Run `mvn test -Dtest=PaperReleaseServiceTest` and verify RED.
- [x] Implement publish by freezing current paper template questions into `paper_release_questions`.
- [x] Implement target expansion from explicit `studentIds` and `classroomIds`, then de-duplicate by student ID.
- [x] Store `publishedByUserId`, optional source classroom IDs in targets, start time, deadline, blank-answer policy, visibility, and total score.
- [x] Create attempts with `NOT_STARTED` for all frozen targets.
- [x] Implement release correction actions with required reasons and audit timestamps.
- [x] Run `mvn test -Dtest=PaperReleaseServiceTest,PaperTemplateServiceTest,ClassroomServiceTest`.
- [x] Commit paper release publishing (included in the aggregate exam-paper feature commit).

### Task 7: Student Assigned Papers, Draft Save, And Final Submit

**Files:**
- Create: `src/main/java/com/example/words/service/StudentPaperAttemptService.java`
- Create: `src/main/java/com/example/words/controller/StudentAssignedPaperController.java`
- Create DTOs:
  - `StudentAssignedPaperSummaryResponse`
  - `StudentPaperAttemptResponse`
  - `SaveStudentPaperDraftRequest`
  - `StudentPaperAnswerRequest`
  - `SubmitStudentPaperRequest`
  - `SubmitStudentPaperResponse`
  - `StudentPaperResultResponse`
- Test: `src/test/java/com/example/words/service/StudentPaperAttemptServiceTest.java`

**Interfaces:**
- Consumes: `ExamPaperAnswerNormalizer`, release snapshots, attempt repositories.
- Produces: student endpoints under `/api/students/me/papers`.
- Produces: idempotent final submission behavior.
- Produces: auto-grading for all first-phase question types.

- [x] Write failing tests for assigned paper list, scheduled paper shell visibility before start, rejecting answer before start, opening attempt moves to `IN_PROGRESS`, draft save, resume from draft, concurrent draft version conflict, final submit locking answers, duplicate submit idempotency, rejecting draft after final submit, `ALLOW_BLANK` scoring unanswered as zero, `REQUIRE_ALL_ANSWERED` rejection, on-time submission, overdue transition, and late submission.
- [x] Write failing grading tests for exact single-choice, exact all-or-nothing multiple-choice, fill-in-the-blank trimming, English case-insensitive matching, multiple accepted blank answers, and incorrect answers receiving zero.
- [x] Write failing request-shape tests against frozen release options: single choice accepts at most one valid key, multiple choice accepts distinct valid keys, fill-in-the-blank accepts at most one value and no option keys, and final submission enforces the release blank-answer policy.
- [x] Run `mvn test -Dtest=StudentPaperAttemptServiceTest` and verify RED.
- [x] Implement student-only endpoints under `/api/students/me/papers`.
- [x] Implement attempt open/save/submit with optimistic version or updated-at precondition for draft saves.
- [x] Enforce the mandatory transaction lock order for list/open/save/submit: pessimistically lock `PaperRelease` first,
  then the target `StudentPaperAttempt`, matching Task 6 correction flows.
- [x] Add a PostgreSQL-backed correction-versus-submit concurrency test that proves serialization, no deadlock,
  no lost final state, and preservation of `SUBMITTED` versus `SUBMITTED_LATE`.
- [x] Implement deadline evaluation at list/open/save/submit time so overdue attempts remain submittable; persist the first `OVERDUE` observation without repeatedly incrementing the attempt version.
- [x] Exclude `WITHDRAWN`, `INVALIDATED`, and hidden `SUPERSEDED` releases from the normal assigned list; safely persist `SCHEDULED` to `OPEN` after `startTime` while holding the release lock.
- [x] Store answer rows separately from release questions, validate every answer before finalization, and lock final scores at final submission.
- [x] Run `mvn test -Dtest=StudentPaperAttemptServiceTest,PaperReleaseServiceTest`.
- [x] Commit student paper attempts (included in the aggregate exam-paper feature commit).

### Task 8: Teacher Result Review And Result Visibility

**Files:**
- Create: `src/main/java/com/example/words/service/PaperResultReviewService.java`
- Modify: `src/main/java/com/example/words/controller/TeacherPaperReleaseController.java`
- Create DTOs:
  - `PaperReleaseResultOverviewResponse`
  - `PaperReleaseStudentResultResponse`
  - `PaperReleaseQuestionStatResponse`
  - `ReleasePaperResultsRequest`
- Test: `src/test/java/com/example/words/service/PaperResultReviewServiceTest.java`

**Interfaces:**
- Consumes: `ExamPaperAccessService.ensureCanReviewRelease`.
- Produces: release completion overview by attempt status.
- Produces: student answer detail and score detail.
- Produces: per-question correctness statistics.
- Produces: result visibility changes without score recalculation.

- [x] Write failing tests for publishing-teacher review, current responsible-teacher review after class transfer, unrelated teacher denial, student denial for others' attempts, status counts including `SUBMITTED_LATE`, per-question correctness rate, invalidated/withdrawn exclusion from default statistics, held results, score-only student visibility, score-and-answers visibility, and visibility changes after submission.
- [x] Run `mvn test -Dtest=PaperResultReviewServiceTest` and verify RED.
- [x] Implement result overview and detail queries using release/attempt/answer rows, not legacy exam queries.
- [x] Derive effective overdue status from the release deadline at query time for teacher counts and statistics, so attempts that no student has viewed are still reported as overdue.
- [x] Implement result visibility release action that records `resultsReleasedAt` and `resultsReleasedByUserId`.
- [x] Implement student result response filtering based on release visibility.
- [x] Run `mvn test -Dtest=PaperResultReviewServiceTest,StudentPaperAttemptServiceTest`.
- [x] Commit paper result review (included in the aggregate exam-paper feature commit).

### Task 9: Legacy Exam Compatibility And Student Task Aggregation

**Files:**
- Modify: `src/main/java/com/example/words/service/StudentDashboardService.java`
- Create: `src/main/java/com/example/words/dto/StudentAssessmentSummaryResponse.java`
- Create: `src/main/java/com/example/words/dto/StudentAssessmentType.java`
- Create: `src/main/java/com/example/words/dto/StudentAssessmentStatus.java`
- Create: `src/main/java/com/example/words/service/StudentAssessmentService.java`
- Create: `src/main/java/com/example/words/controller/StudentAssessmentController.java`
- Test: `src/test/java/com/example/words/service/StudentDashboardServiceTest.java`
- Test: `src/test/java/com/example/words/service/ExamServiceTest.java`
- Test: `src/test/java/com/example/words/service/StudentAssessmentServiceTest.java`

**Interfaces:**
- Consumes: legacy `Exam` history and new `StudentPaperAttempt`.
- Produces: student-visible task/history responses with an explicit type discriminator, such as `LEGACY_GENERATED_EXAM` and `PAPER_RELEASE_ATTEMPT`.
- Produces: `List<StudentAssessmentSummaryResponse> listPending(AppUser actor)`.
- Produces: `List<StudentAssessmentSummaryResponse> listHistory(AppUser actor)`.

- [x] Write failing tests proving existing generated exams still create, submit, and show history as before.
- [x] Write failing tests proving pending/history responses include enough type information for frontend routing.
- [x] Write failing tests proving paper attempts appear in pending tasks before start as scheduled shells and after deadline as overdue.
- [x] Derive effective overdue status from release deadlines in aggregation queries instead of relying only on persisted attempt status, including never-viewed attempts.
- [x] Run `mvn test -Dtest=StudentAssessmentServiceTest,StudentDashboardServiceTest,ExamServiceTest` and verify RED only for new assessment aggregation behavior.
- [x] Implement `StudentAssessmentService` as the aggregation boundary so word-learning dashboard queue DTOs stay word-task-specific.
- [x] Add student endpoints under `/api/students/me/assessments` for pending and history lists.
- [x] Keep legacy `Exam` persistence and submission semantics unchanged.
- [x] Run `mvn test -Dtest=StudentAssessmentServiceTest,StudentDashboardServiceTest,ExamServiceTest`.
- [x] Commit student assessment aggregation (included in the aggregate exam-paper feature commit).

### Task 10: Student Points And Learning Analytics Hooks

**Files:**
- Create: `src/main/resources/db/migration/V40__add_paper_attempt_point_source.sql`
- Modify: `src/main/java/com/example/words/model/PointSourceType.java`
- Modify: `src/main/java/com/example/words/service/StudentPaperAttemptService.java`
- Test: `src/test/java/com/example/words/service/StudentPaperAttemptPointEventTest.java`
- Test: `src/test/java/com/example/words/service/StudentPointRuleServiceTest.java`
- Test: `src/test/java/com/example/words/service/StudentPointEventServiceTest.java`

**Interfaces:**
- Consumes: existing student point event publisher and rule system.
- Produces: distinguishable source type `PAPER_RELEASE_ATTEMPT` for future paper-attempt rules.
- Produces: no default point event publication from paper submission in the first phase.
- Produces: attempt fields and source keys that future point rules can distinguish on-time vs late submission status.

- [x] Write failing tests proving paper submission does not publish a student point event by default.
- [x] Write failing tests proving `StudentPointRuleService` accepts configurable rules with source type `PAPER_RELEASE_ATTEMPT` after the enum and database constraint are updated.
- [x] Write failing tests proving late submissions persist enough attempt status/source-key data for future rules to distinguish on-time and late attempts.
- [x] Run `mvn test -Dtest=StudentPaperAttemptPointEventTest,StudentPointRuleServiceTest,StudentPointEventServiceTest`.
- [x] Add Flyway migration `V40__add_paper_attempt_point_source.sql` that drops and recreates `ck_student_point_transactions_source_type`, `ck_student_point_events_source_type`, and `ck_student_point_rules_source_type` with `PAPER_RELEASE_ATTEMPT` included.
- [x] Add `PAPER_RELEASE_ATTEMPT` to `PointSourceType`.
- [x] Keep `StudentPaperAttemptService` free of `StudentPointEventPublisher` calls in the first phase.
- [x] Run focused tests plus `mvn test -Dtest=StudentPaperAttemptPointEventTest,StudentPointRuleServiceTest,StudentPointEventServiceTest,StudentPaperAttemptServiceTest`.
- [x] Commit paper-attempt points integration (included in the aggregate exam-paper feature commit).

### Task 11: Teacher/Admin Frontend

**Files:**
- Modify: `frontend/admin/src/lib/api.ts`
- Modify: `frontend/admin/src/types/api.ts`
- Modify: `frontend/admin/src/App.tsx`
- Modify: `frontend/admin/src/components/layout/app-shell.tsx`
- Modify: `frontend/admin/src/components/layout/navigation.ts`
- Create: `frontend/admin/src/pages/question-bank-page.tsx`
- Create: `frontend/admin/src/pages/question-import-page.tsx`
- Create: `frontend/admin/src/pages/paper-template-page.tsx`
- Create: `frontend/admin/src/pages/paper-editor-page.tsx`
- Create: `frontend/admin/src/pages/paper-release-page.tsx`
- Create: `frontend/admin/src/pages/paper-result-page.tsx`
- Modify: `frontend/admin/src/index.css`

**Interfaces:**
- Consumes: teacher question, import, paper, release, and result review APIs.
- Produces: dense teacher workflows for search/filter/import/select/preview/publish/review.

- [x] Add API client methods and TypeScript types for all teacher endpoints.
- [x] Build question bank page with keyword/type/status/tag filters, create/edit/archive/copy actions, and detail preview.
- [x] Build CSV import page with upload, preview table, invalid row messages, duplicate candidate markers, and confirm selected valid rows.
- [x] Build paper list/editor pages with create draft, add question, reorder, score edit, total score, preview, copy, and archive.
- [x] Build release page with publish to students/classes, start/deadline, blank policy, result visibility, target preview, withdraw, invalidate, and supersede actions.
- [x] Build result review page with completion status counts, student scores, late markers, per-question correctness, answer details, and visibility release controls.
- [x] Run `npm run build` in `frontend/`.
- [x] Rebuild frontend container with `docker-compose up -d --build frontend`.
- [x] Commit teacher paper management UI (included in the aggregate exam-paper feature commit).

### Task 12: Student Frontend

**Files:**
- Modify: `frontend/src/api/index.ts`
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.css`
- Modify: `frontend/src/student/StudentWorkspace.tsx`
- Modify: `frontend/src/student/student-workspace-state.ts`
- Modify: `frontend/src/student/student-workspace.css`
- Create: `frontend/src/student/StudentAssignedPapers.tsx`
- Create: `frontend/src/student/StudentPaperAttempt.tsx`
- Create: `frontend/src/student/StudentPaperResult.tsx`

**Interfaces:**
- Consumes: `/api/students/me/papers` endpoints and dashboard aggregation metadata.
- Produces: student pending-paper list, scheduled shell, answer page, draft save, final submit, late marker, and permitted result view.

- [x] Add API client methods and TypeScript types for assigned papers, attempt detail, save draft, submit, and result.
- [x] Build pending-paper list with status labels: not started, in progress, submitted, submitted late, overdue, invalidated.
- [x] Build scheduled shell that shows title, instructions, start time, deadline, question count, and total score but disables answering before start.
- [x] Build answer page for single-choice, multiple-choice, and fill-in-the-blank, including stable controls and draft-save state.
- [x] Build final submit confirmation and post-submit result display based on visibility mode.
- [x] Run `npm run build` in `frontend/`.
- [x] Rebuild frontend container with `docker-compose up -d --build frontend`.
- [x] Commit student paper answering UI (included in the aggregate exam-paper feature commit).

### Task 13: End-To-End Verification And Hardening

**Files:**
- Add or update integration tests where service-level tests cannot cover transaction boundaries.
- Update `docs/requirements/exam-question-paper-requirements.zh-CN.md` only if implementation discovers a real requirement correction.
- Update API notes or README if the project keeps endpoint documentation current.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: verified first-phase exam paper workflow.

- [x] Run backend tests: `mvn test` (560 run; 25 pre-existing student-points assertion failures, focused exam-paper suite passes).
- [x] Run frontend build: `npm run build` in `frontend/`.
- [x] Rebuild frontend container: `docker-compose up -d --build frontend`.
- [x] Manually verify teacher flow: create questions, import CSV, create paper, add questions, preview, publish to a class.
- [x] Manually verify student flow: view scheduled paper shell, start after start time, save draft, resume, submit on time, and view released score plus answers.
- [x] Manually verify late flow: advance release test data beyond its deadline, submit afterward, confirm `SUBMITTED_LATE` and teacher late marker.
- [x] Manually verify permission flow: unrelated teacher cannot review; publishing teacher can review; current responsible class teacher can review.
- [x] Manually verify correction flow: withdraw before submission, reject withdrawal after submission, invalidate after submission, supersede with corrected release for full original frozen targets.
- [x] Manually verify legacy flow: create and submit existing dictionary-generated exam through `/api/exams`.
- [x] Run `git status --short` and separate unrelated pre-existing changes from this feature's commits.
- [x] Commit final verified implementation (included in the aggregate exam-paper feature commit).

---

## Recommended Development Order

1. Task 1 creates the database and entity foundation.
2. Task 2 centralizes permission and normalization so later tasks do not duplicate rules.
3. Task 3 delivers a working question-bank slice.
4. Task 4 adds import after manual creation is stable.
5. Task 5 delivers paper authoring.
6. Task 6 publishes immutable releases and creates attempts.
7. Task 7 delivers the student answering loop.
8. Task 8 adds teacher review and visibility behavior.
9. Task 9 integrates the new flow into student assessment lists without breaking legacy exams.
10. Task 10 adds the explicit future-proof seam for points and analytics.
11. Task 11 and Task 12 complete teacher and student frontend workflows.
12. Task 13 verifies the whole feature.

## Non-Blocking Product Defaults

- Administrator maintenance default: admins can view, audit, copy, archive, withdraw, invalidate, and review, but should not directly edit another teacher's paper content in the first phase.
- Scheduled assignment visibility default: assigned students can see the task shell before start time but cannot answer.
- Superseded original visibility default: hide from normal student task/history lists while preserving teacher audit access.
- CSV duplicate behavior default: flag duplicate candidates for teacher review; do not merge automatically.
- Fill-in-the-blank grading default: trim and compare English case-insensitively; do not use fuzzy matching or AI grading.

## Self-Review Checklist

- [x] Requirements covered: question bank, import, paper template, release, target freeze, student attempt, late submission, result visibility, correction, permissions, compatibility, and tests.
- [x] No task depends on mutating legacy `exams` or `exam_questions` for new paper behavior.
- [x] No normal teacher workflow physically deletes historical question, paper, release, attempt, or answer records.
- [x] Every task has a focused test cycle and can be reviewed independently.
- [x] Frontend tasks include required `npm run build` and Docker frontend rebuild.
