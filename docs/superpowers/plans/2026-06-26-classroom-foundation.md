# Classroom Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the first classroom rules slice: global unique classroom names, classroom archive status, archive-first deletion, and archived-classroom write guards.

**Architecture:** Keep the behavior in the existing Spring service layer. `ClassroomService` owns classroom lifecycle and member guards; `ClassroomDictionaryAssignmentService` and `StudyPlanService` reject archived classrooms before creating new downstream work. Persistence uses a `ClassroomStatus` enum plus Flyway migration.

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA, Flyway, JUnit 5, Mockito.

## Global Constraints

- Follow `REQ-ORG-0001@3.1.0`: classroom names are globally unique, including archived classrooms.
- Follow `REQ-ORG-0001@3.1.0`: default classroom deletion archives classrooms with history.
- Follow `REQ-ORG-0001@3.1.0`: physical deletion is only for empty classrooms with no history.
- Follow `REQ-STUDY-0002@1.5.0`: archived classrooms cannot be used to create new study plans.
- Keep changes scoped to the first implementation slice.

---

### Task 1: Classroom Lifecycle Model And Service Rules

**Files:**
- Create: `src/main/java/com/example/words/model/ClassroomStatus.java`
- Modify: `src/main/java/com/example/words/model/Classroom.java`
- Modify: `src/main/java/com/example/words/repository/ClassroomRepository.java`
- Modify: `src/main/java/com/example/words/service/ClassroomService.java`
- Test: `src/test/java/com/example/words/service/ClassroomServiceTest.java`

**Interfaces:**
- Produces: `ClassroomStatus.ACTIVE`, `ClassroomStatus.ARCHIVED`
- Produces: `ClassroomRepository.existsByName(String name)`
- Produces: archived classroom rejection in `ClassroomService.addStudentToClassroom`

- [ ] Write failing tests for duplicate names, archive delete with history, physical delete for empty classrooms, and archived member rejection.
- [ ] Run `./mvnw test -Dtest=ClassroomServiceTest` and verify failures come from missing behavior.
- [ ] Add `ClassroomStatus`, classroom status fields, repository methods, and minimal service logic.
- [ ] Run `./mvnw test -Dtest=ClassroomServiceTest` and verify green.

### Task 2: Downstream Archived-Classroom Guards

**Files:**
- Modify: `src/main/java/com/example/words/service/ClassroomDictionaryAssignmentService.java`
- Modify: `src/main/java/com/example/words/service/StudyPlanService.java`
- Test: `src/test/java/com/example/words/service/ClassroomDictionaryAssignmentServiceTest.java`
- Test: `src/test/java/com/example/words/service/StudyPlanServiceTest.java`

**Interfaces:**
- Consumes: `ClassroomStatus.ARCHIVED`
- Produces: archived classroom rejection for dictionary assignment and study plan creation.

- [ ] Write failing tests for assigning dictionaries to archived classrooms and creating study plans from archived classrooms.
- [ ] Run targeted tests and verify failures.
- [ ] Add minimal archived-classroom guards in the two services.
- [ ] Run targeted tests and verify green.

### Task 3: Persistence Migration And Regression

**Files:**
- Create: `src/main/resources/db/migration/V27__add_classroom_archive_and_unique_name.sql`
- Modify tests affected by `Classroom` constructor shape as needed.

**Interfaces:**
- Produces database columns `classrooms.status`, `classrooms.archived_at`
- Produces unique constraint on `classrooms.name`

- [ ] Add Flyway migration for status, archived timestamp, and global unique classroom name.
- [ ] Run `./mvnw test -Dtest=ClassroomServiceTest,ClassroomDictionaryAssignmentServiceTest,StudyPlanServiceTest`.
- [ ] Run `./mvnw test` if targeted tests pass.
