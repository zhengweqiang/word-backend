# 试题与试卷管理 PRD

## Problem Statement

当前系统已经具备从词书生成一次性考试、学生提交答案、查看结果的基础能力，但这类考试更像“按词书临时生成的测验”。老师在实际教学中还需要维护可复用的试题库：可以手动录入试题，也可以批量导入试题；可以从题库中挑选一批试题编纂成试卷；可以把试卷发布给一个或多个学生；学生收到试卷后完成作答，系统记录每道题的答案、提交状态和成绩结果。

如果没有独立的试题与试卷管理能力，老师每次布置测验都需要重新生成或重新整理题目，难以沉淀高质量题库，也难以复用同一份试卷进行班级测验、阶段测验或补测。

## Solution

建设一套围绕“题库试题、试卷、试卷发布、学生作答”的考试管理能力。

老师可以创建和维护题库试题，第一阶段重点支持可自动判分的客观题，包括单选题、多选题和填空题。老师可以通过页面逐题录入，也可以通过结构化文件批量导入。系统在导入时校验题干、题型、选项、答案、分值、解析等字段，并给出成功、失败和重复项反馈。

老师可以从题库中选择试题编纂成试卷。试卷保存为可复用的教学资源，包含标题、说明、总分、题目顺序、每题分值、是否打乱题目、是否打乱选项、发布时间和截止时间等配置。试卷发布后，系统为每个学生生成独立的作答记录，学生在自己的待完成任务中看到试卷并完成作答。

学生提交试卷后，系统对客观题自动判分，保存每道题的作答明细、正确性、得分和提交时间。老师可以查看发布记录、学生完成情况、学生成绩、每题正确率，并可按试卷追踪教学效果。

## Domain Language

- 试题：由老师或管理员维护的可复用测评题目。试题是题库资产，不等同于某个学生实际作答时看到的题目记录。
- 共享题库：所有老师都可以检索和使用的试题集合。试题保留创建者和导入来源用于追溯，但老师之间不按所有者限制组卷使用。
- 试题复制：老师基于共享题库中的既有试题创建一份新的独立试题。复制后的试题由复制者维护，后续修改不会影响原试题。
- 试卷：老师可复用、可编辑的题目集合模板，包含题目顺序、分值和展示配置。试卷不是考试记录，也不代表某个学生已经收到或完成了测验。
- 试卷复制：老师基于其他老师的试卷创建一份新的独立试卷。复制后的试卷由复制者维护，后续修改和发布不会影响原试卷。
- 试卷发布版本：每次试卷发布时冻结形成的试卷内容版本。后续编辑试卷只影响未来发布，不改变既有试卷发布版本。
- 试卷发布：老师把某份试卷下发给一批学生或班级的一次教学动作。试卷发布确定学生范围、作答时间、结果可见性和学生入口。
- 正式考试：学生视角里由老师或管理员发布并需要完成的测验。正式考试可以由试卷发布产生，成绩进入老师评价。
- 自测练习：学生自己基于已分配学习资源发起的练习，不进入老师正式考试完成率或评价统计。
- 学生作答记录：某个学生针对一次试卷发布版本形成的个人作答状态和答案记录。它不是试卷本身，也不是发布本身。

## User Stories

1. As a teacher, I want to manually create a question, so that I can quickly add a new assessment item during lesson preparation.
2. As a teacher, I want to choose a question type when creating a question, so that the system can collect the right answer format and scoring rules.
3. As a teacher, I want to create a single-choice question with multiple options and one correct answer, so that students can answer vocabulary or comprehension questions.
4. As a teacher, I want to create a multiple-choice question with multiple correct answers, so that I can assess compound knowledge points.
5. As a teacher, I want to create a fill-in-the-blank question with one or more accepted answers, so that students can practice spelling or recall.
6. As a teacher, I want to set a score for each question, so that different questions can carry different weights.
7. As a teacher, I want to add an explanation to a question, so that students can understand why an answer is correct after submission.
8. As a teacher, I want to tag questions by grade, textbook, unit, difficulty, or knowledge point, so that I can find suitable questions later.
9. As a teacher, I want to associate questions with existing words or dictionaries when relevant, so that vocabulary questions can reuse the system's word learning data.
10. As a teacher, I want to edit a draft question, so that I can fix mistakes before using it in a paper.
11. As a teacher, I want to disable or archive a question that has problems, so that it is not selected into new papers accidentally.
12. As a teacher, I want existing papers and submitted answers to keep their historical question snapshots, so that later edits to the question bank do not change past exam records.
13. As a teacher, I want to import questions from a structured file, so that I can migrate existing teaching materials into the system quickly.
14. As a teacher, I want the import process to validate required fields, so that invalid questions do not enter the question bank.
15. As a teacher, I want import errors to identify the row and reason, so that I can correct the source file efficiently.
16. As a teacher, I want duplicate detection during import, so that repeated questions can be skipped or reviewed.
17. As a teacher, I want a preview before confirming imported questions, so that I can catch formatting mistakes before publishing them into the question bank.
18. As a teacher, I want to search questions by keyword, type, tag, difficulty, and creation source, so that I can assemble papers quickly.
19. As a teacher, I want questions imported or created by any teacher to be available in the shared question bank, so that the teaching team can reuse each other's materials.
20. As a teacher, I want to use another teacher's question in my paper without changing the source question, so that shared reuse does not damage the original material.
21. As a teacher, I want to copy another teacher's question into a new question before editing it, so that I can adapt it for my class without affecting the original.
22. As a teacher, I want to create a paper from selected questions, so that I can build a reusable assessment.
23. As a teacher, I want to reorder questions inside a paper, so that the paper follows my intended teaching sequence.
24. As a teacher, I want to adjust per-question scores inside a paper, so that the same question can have a different weight in different papers.
25. As a teacher, I want the paper total score to be calculated automatically, so that I do not need to add scores manually.
26. As a teacher, I want to save a paper as draft, so that I can continue editing it later.
27. As a teacher, I want to preview a paper as students will see it, so that I can verify the final experience before release.
28. As a teacher, I want to publish a paper to selected students, so that only intended students receive the assignment.
29. As a teacher, I want to publish a paper to a class, so that the whole class receives the same assessment.
30. As a teacher, I want to set a start time and deadline when publishing a paper, so that students can only answer during the intended window.
31. As a teacher, I want to decide whether students can submit after the deadline, so that I can handle strict exams and flexible homework differently.
32. As a teacher, I want to see which students have not started, are answering, have submitted, or are overdue, so that I can follow up.
33. As a teacher, I want to see each student's score and answer details, so that I can understand individual learning gaps.
34. As a teacher, I want to see per-question correctness statistics, so that I can identify difficult knowledge points.
35. As a teacher, I want to reuse an existing paper for another class or student group, so that I do not need to rebuild the same assessment.
36. As a teacher, I want to copy another teacher's paper into a new editable paper, so that I can adapt a useful paper without affecting the original.
37. As a teacher, I want other teachers to be unable to directly edit my paper, so that my reusable assessment remains stable.
38. As a teacher, I want published papers to be immutable for students already assigned, so that every student in the same release answers the same content.
39. As a teacher, I want each publish action to create a new paper release version, so that previous releases remain traceable while future releases can use updated content.
40. As a teacher, I want to invalidate a mistaken release with an explicit reason, so that incorrect assessments are preserved for audit but excluded from normal evaluation.
41. As a teacher, I want to publish a corrected version after finding a mistake, so that students receive the corrected paper without overwriting the original release.
42. As a student, I want to see papers assigned to me in my pending tasks, so that I know what I need to complete.
43. As a student, I want to open a paper and see its title, instructions, question count, total score, and deadline, so that I understand the task before answering.
44. As a student, I want to answer single-choice questions by selecting one option, so that I can complete objective questions quickly.
45. As a student, I want to answer multiple-choice questions by selecting multiple options, so that I can respond to questions with more than one correct answer.
46. As a student, I want to answer fill-in-the-blank questions by typing text, so that I can practice recall and spelling.
47. As a student, I want my in-progress answers to be saved, so that I do not lose work if I leave the page accidentally.
48. As a student, I want the system to prevent duplicate final submissions, so that my score is not changed accidentally after submission.
49. As a student, I want to resume an in-progress paper from my saved answers, so that I can continue after refreshing, switching devices, or briefly losing network connectivity.
50. As a student, I want final submission to lock my answers, so that the submitted result is stable and auditable.
51. As a teacher, I want to distinguish not started, in progress, submitted, overdue, and invalidated attempts, so that I can follow up with students accurately.
52. As a student, I want to see my score after submission when the teacher allows it, so that I can understand my result immediately.
53. As a student, I want to see correct answers and explanations after submission when the teacher allows it, so that I can review mistakes.
54. As a student, I want overdue papers to be clearly marked, so that I know which tasks can no longer be submitted normally.
55. As an administrator, I want to audit who created or imported each question, so that shared teaching resources remain traceable.
56. As an administrator, I want to archive problematic shared questions, so that teachers stop using invalid material while historical papers remain stable.
57. As a system, I want to keep question-bank data separate from submitted answer records, so that historical exams remain stable even when source questions change.
58. As a system, I want to enforce teacher-student and class permission boundaries, so that teachers can only publish to students they are responsible for.
59. As a system, I want to store answer snapshots for each published paper, so that grading and result review do not depend on mutable question-bank records.
60. As a system, I want to reject malformed imported questions before they become usable, so that students never receive broken papers.

## Implementation Decisions

- Introduce a dedicated question bank concept instead of treating every exam question as a one-off generated question. A question-bank item is the reusable source record; a paper question is a snapshot of that source at the moment it is added to a paper or published.
- Treat a paper as a reusable template, not as a student-facing exam record. A formal exam is the student-facing assessment created from a paper release.
- Keep “question”, “paper”, “paper release”, and “student attempt” as separate domain concepts:
  - Question: reusable teaching asset maintained by teachers or administrators.
  - Paper: reusable collection of ordered question snapshots and scoring rules.
  - Paper release: one publication of a paper to students or classes, with time window and visibility rules.
  - Student attempt: one student's answer state, submission record, score, and per-question answer details.
- Extend the current exam capability rather than replacing it immediately. Existing generated exams can continue serving dictionary-based quick quizzes, while the new paper flow supports teacher-authored formal assessments.
- First-phase supported question types are single-choice, multiple-choice, and fill-in-the-blank because they can be automatically graded. Subjective questions, manual marking, attachments, audio questions, and essay grading are out of scope for the first phase.
- The question bank is shared across teachers. Questions imported or created by any teacher are available for all teachers to search, view, and use in papers.
- Question records should keep creator, importer, import batch, and last modifier metadata for traceability, but teacher-to-teacher usage should not be restricted by question owner.
- Teachers can edit questions they created or imported. Administrators can edit or archive any question.
- Teachers cannot directly edit questions created or imported by another teacher. To adapt another teacher's question, a teacher must copy it into a new independent question and then edit that copy.
- Copied questions should keep a source-question reference for traceability, but later edits to the copy must not affect the source question.
- Students cannot create, modify, archive, or directly browse question-bank records.
- Teacher access follows existing teacher-student and classroom relationships. A teacher can publish a paper only to students or classes they are allowed to manage.
- Imported questions must pass validation before being saved as active question-bank items. Invalid rows should be returned with row numbers and user-readable reasons.
- First-phase import supports CSV only. Excel, Word, PDF, and image recognition imports are outside the first phase.
- Import should support a stable, documented CSV template with fields for `questionType`, `stem`, `optionA`, `optionB`, `optionC`, `optionD`, `correctAnswers`, `score`, `difficulty`, `tags`, `explanation`, `dictionaryName`, and `word`.
- Each CSV row represents one question. Single-choice and multiple-choice rows require options and `correctAnswers`; fill-in-the-blank rows do not require options but require one or more accepted answers in `correctAnswers`.
- CSV import should run as preview before confirmation. Preview returns valid rows, invalid rows, duplicate candidates, row numbers, and user-readable failure reasons. Only confirmed valid rows are saved into the question bank.
- Duplicate detection should compare normalized stem, question type, normalized options, and normalized correct answer across the shared question bank. The first phase can flag duplicates for review instead of automatically merging them.
- Editing a question-bank item must not mutate already-published papers or already-submitted attempts. Historical paper content and grading data should remain stable.
- A paper can be saved as draft before publishing. Draft papers can be edited freely.
- Teachers can search and view papers created by other teachers for reuse. They cannot directly edit another teacher's paper.
- To adapt another teacher's paper, a teacher must copy it into a new independent paper and then edit the copy. The copied paper should keep a source-paper reference for traceability.
- Every publish action creates a new immutable paper release version. Later edits to the reusable paper template affect only future releases and never overwrite existing paper release versions.
- A paper release version is the content students answer against. Students in the same release receive the same frozen question order, question text, options, accepted answers, per-question scores, and grading configuration.
- If a release has no submitted attempts, the teacher can withdraw it with an explicit reason. If any student has submitted, the release can be invalidated or superseded by publishing a corrected version, but it should not be silently edited in place.
- Publishing a paper to a class expands to the eligible student list at publish time. Later class membership changes should not silently alter an existing release unless an explicit republish or add-student action is introduced.
- Student answers should be saved separately from the source questions and paper definitions. This allows progress saving, final submission, grading, and historical review.
- Student attempts should support these lifecycle states:
  - NOT_STARTED: the student has received the paper but has not opened or saved any answer.
  - IN_PROGRESS: the student has opened the paper or saved at least one draft answer.
  - SUBMITTED: the student has finalized the attempt; answers and score are locked.
  - OVERDUE: the deadline has passed before final submission, according to the release's late-submission rule.
  - INVALIDATED: the release or attempt has been explicitly marked invalid and excluded from normal evaluation.
- Draft answers should be saved while the attempt is IN_PROGRESS so students can resume after refresh, device switch, or short network interruption.
- Only final submission should trigger locked grading results. Saving draft answers should not update formal scores or teacher evaluation statistics.
- Final submission should be idempotent from the student's perspective: once submitted, repeated submit attempts should not create duplicate attempts or alter the final score unless a future explicit reopen workflow exists.
- Auto-grading rules:
  - Single-choice is correct when the selected option exactly matches the stored correct option.
  - Multiple-choice is correct only when the selected option set exactly matches the stored correct option set. First phase does not support partial credit, deduction for wrong options, or separate scoring for missed options.
  - Fill-in-the-blank is correct when the normalized text exactly matches one accepted answer. Normalization should trim surrounding whitespace and compare English answers case-insensitively. Teachers can provide multiple accepted answers to cover spelling variants, but first phase does not support fuzzy matching, typo tolerance, AI grading, or semantic similarity.
- Result visibility should be configurable per release: teachers can decide whether students immediately see score only, score plus correct answers, or no result until teacher release.
- Use restrictive relationships and explicit cleanup operations for new data. New foreign keys should not introduce cascading deletes for question, paper, release, or attempt history.
- The API surface should be organized around teacher question management, teacher paper management, teacher release management, student assigned papers, student attempts, and result review.
- The frontend should present the feature as a working teaching tool, not as a marketing page: teachers need dense search, filter, import, selection, preview, publish, and result-review workflows; students need a clear pending-paper and answering workflow.

## Testing Decisions

- The highest-value test seam is the authenticated REST API boundary because this feature is primarily about role permissions, state transitions, persistence, and student-visible behavior.
- Tests should verify behavior through public APIs instead of internal service implementation details. The same tests should still pass if the internal data model is refactored but the business behavior remains correct.
- Teacher question-management tests should cover creating questions, validation failures, editing one's own questions, rejecting direct edits to another teacher's questions, copying another teacher's question into a new editable question, archiving questions, search and filtering, and cross-teacher reuse of shared questions.
- Import tests should cover a valid CSV file, missing required fields, invalid answer formats, multiple accepted answers for fill-in-the-blank questions, duplicate rows, unsupported question types, preview-before-confirm behavior, and partial failure reporting.
- Paper-management tests should cover creating a draft paper, adding questions, reordering questions, changing per-question scores, previewing paper content, rejecting direct edits to another teacher's paper, copying another teacher's paper into a new editable paper, and preserving question snapshots.
- Publishing tests should cover publishing to one student, multiple students, a class, unauthorized students, start time, deadline, and release visibility settings.
- Student attempt tests should cover opening an assigned paper, saving progress, submitting answers, duplicate submission protection, auto-grading for each supported question type, all-or-nothing multiple-choice grading, and deadline behavior.
- Result-review tests should cover teacher access to student submissions, per-question correctness statistics, student result visibility rules, and permission denial for unrelated teachers or students.
- Regression tests should cover the existing generated-exam flow so that adding the new paper flow does not break current dictionary-based quizzes.
- Database migration verification should check that new tables and constraints are created without relying on cascading deletes and that historical attempt records remain available after source question changes.

## Out of Scope

- Subjective questions, manual teacher marking, rubric scoring, essay grading, and teacher feedback comments.
- Partial credit for multiple-choice questions, deduction for wrong options, and per-option scoring.
- Fuzzy matching, typo tolerance, AI grading, or semantic-similarity grading for fill-in-the-blank answers.
- Audio, image, video, or attachment-based questions.
- Excel, Word, PDF, image recognition, or AI-based question extraction imports.
- Random paper generation from rules such as “pick 10 easy and 5 hard questions”.
- Anti-cheating features such as fullscreen locking, camera monitoring, IP restrictions, or tab-switch detection.
- Real-time classroom proctoring.
- Retake, reopen, or rescore workflows after final submission.
- Public marketplace sharing of question banks across schools or organizations.
- Advanced analytics such as long-term ability modeling or adaptive recommendation.
- Replacing existing dictionary-generated quick exams in the same phase.

## Further Notes

- This PRD assumes the first deliverable is a teacher-authored assessment workflow for classroom use, not a high-stakes examination system.
- The current system already has users, roles, teacher-student relationships, classrooms, dictionaries, generated exams, and student submission concepts. The new work should reuse those boundaries where possible.
- The wording in UI and API should distinguish “试题”, “试卷”, “发布”, and “作答记录” clearly. Mixing these concepts will make future grading, analytics, and retake workflows harder.
- The first implementation plan should likely be split into vertical slices: question bank foundation, import flow, paper draft editing, publish to students, student answering, and teacher result review.
- Before implementation, confirm whether the product wants to call the reusable assessment object “试卷” consistently, while keeping existing generated `Exam` wording for legacy quick quizzes.
