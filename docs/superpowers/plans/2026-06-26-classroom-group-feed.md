# Classroom Group Feed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the backend for classroom group feed text messages plus dictionary and video resource entry messages.

**Architecture:** Add a persisted `ClassroomGroupFeedMessage` aggregate keyed by `classroomId`. `ClassroomGroupFeedService` owns classroom membership checks, archived-classroom guards, resource-share validation, and response enrichment. `ClassroomGroupFeedController` exposes feed endpoints under existing classroom routes.

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA, Flyway, JUnit 5, Mockito.

## Global Constraints

- Follow `REQ-ORG-0004@1.1.0`: every classroom has an implicit group feed; no separate feed create/join workflow.
- Follow `REQ-ORG-0004@1.1.0`: feed visibility and text posting are limited to the current classroom teacher and current classroom student members.
- Follow `REQ-ORG-0004@1.1.0`: dictionary share messages require the dictionary to already be assigned to the classroom.
- Follow `REQ-ORG-0004@1.1.0`: video share messages require a shareable video that is READY and PUBLISHED.
- Follow `REQ-ORG-0004@1.1.0`: resource messages create only entry messages and do not grant resource permissions.
- Follow `REQ-ORG-0001@3.1.0`: archived classrooms cannot access the current classroom group feed.

---

### Task 1: Feed Persistence, Service, And Controller

**Files:**
- Create: `src/main/java/com/example/words/model/ClassroomGroupFeedMessage.java`
- Create: `src/main/java/com/example/words/model/ClassroomGroupFeedMessageType.java`
- Create: `src/main/java/com/example/words/repository/ClassroomGroupFeedMessageRepository.java`
- Create: `src/main/java/com/example/words/dto/CreateClassroomGroupFeedTextMessageRequest.java`
- Create: `src/main/java/com/example/words/dto/ShareClassroomGroupFeedDictionaryRequest.java`
- Create: `src/main/java/com/example/words/dto/ShareClassroomGroupFeedVideoRequest.java`
- Create: `src/main/java/com/example/words/dto/ClassroomGroupFeedMessageResponse.java`
- Create: `src/main/java/com/example/words/service/ClassroomGroupFeedService.java`
- Create: `src/main/java/com/example/words/controller/ClassroomGroupFeedController.java`
- Create: `src/main/resources/db/migration/V28__create_classroom_group_feed_messages.sql`
- Test: `src/test/java/com/example/words/service/ClassroomGroupFeedServiceTest.java`

**Interfaces:**
- Produces: `GET /api/classrooms/{classroomId}/group-feed/messages`
- Produces: `POST /api/classrooms/{classroomId}/group-feed/messages`
- Produces: `POST /api/classrooms/{classroomId}/group-feed/dictionaries`
- Produces: `POST /api/classrooms/{classroomId}/group-feed/videos`

- [ ] Write failing service tests for text message creation by student and teacher.
- [ ] Write failing service tests for non-member access denial and archived-classroom access denial.
- [ ] Write failing service tests for dictionary shares requiring existing classroom assignment.
- [ ] Write failing service tests for video shares requiring teacher ownership/manage permission plus READY/PUBLISHED status.
- [ ] Add entity, repository, DTOs, service, controller, and migration.
- [ ] Run targeted tests and then full `mvn test`.
