# Classroom Study Plan Publish Revalidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure draft study plans revalidate classroom and dictionary eligibility at publish time.

**Architecture:** Keep the rule in `StudyPlanService.publishStudyPlan`, using the existing classroom resolution and dictionary visibility checks before creating assignments or student plans. Published plans remain historical artifacts and are not invalidated by later classroom dictionary removals.

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA, JUnit 5, Mockito.

## Global Constraints

- Follow `REQ-STUDY-0002@1.5.0`: draft or unpublished study plans must revalidate classroom dictionary availability at publish time.
- Follow `REQ-ORG-0001@3.1.0`: archived classrooms cannot be used for new study plan work.
- Keep already published study plans and existing student study history valid after later classroom dictionary removal.

---

### Task 1: Publish-Time Classroom And Dictionary Revalidation

**Files:**
- Modify: `src/main/java/com/example/words/service/StudyPlanService.java`
- Test: `src/test/java/com/example/words/service/StudyPlanServiceTest.java`

**Interfaces:**
- Consumes: `StudyPlanService.publishStudyPlan(Long studyPlanId, AppUser actor)`
- Consumes: `StudyPlanClassroomRepository.findByStudyPlanId(Long studyPlanId)`
- Produces: publish-time rejection when the plan dictionary is no longer visible for all plan classrooms.
- Produces: publish-time rejection when a plan classroom is archived.

- [ ] Add failing tests for publishing a draft after its classroom dictionary intersection no longer contains the plan dictionary.
- [ ] Add failing tests for publishing a draft after one of its classrooms has been archived.
- [ ] Run targeted tests and verify they fail for missing publish-time guards.
- [ ] Add minimal publish-time revalidation before student snapshot and dictionary assignment.
- [ ] Run targeted tests and verify they pass.
- [ ] Run full `mvn test`.
