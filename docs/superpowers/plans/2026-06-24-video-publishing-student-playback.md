# Video Publishing Student Playback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let managers publish ready teaching videos and let students list and play only published videos visible to them.

**Architecture:** Keep media processing state separate from publication state by adding `VideoPublishStatus` to `video_assets`. Manager APIs mutate publication state; student APIs use a separate read-only visibility path and return a smaller student-facing DTO.

**Tech Stack:** Spring Boot 3.2, Spring Data JPA Specifications, Flyway, Solid admin frontend, React student frontend, Maven, Vite.

## Global Constraints

- Existing videos must default to `UNPUBLISHED`.
- `VideoStatus` continues to mean cloud media readiness only.
- Students must not use manager `/api/videos/**` endpoints.
- Publishing requires `READY` status and a non-blank playable URL.
- Teacher-scoped videos are student-visible only when the owner teacher is responsible for the student.
- Frontend changes under `frontend/` require rebuilding and restarting the Docker `frontend` container before completion.

---

### Task 1: Backend Publication Model and Manager APIs

**Files:**
- Create: `src/main/java/com/example/words/model/VideoPublishStatus.java`
- Modify: `src/main/java/com/example/words/model/VideoAsset.java`
- Modify: `src/main/java/com/example/words/dto/VideoResponse.java`
- Modify: `src/main/java/com/example/words/service/VideoAssetService.java`
- Modify: `src/main/java/com/example/words/controller/VideoController.java`
- Create: `src/main/resources/db/migration/V26__add_video_publish_status.sql`
- Test: `src/test/java/com/example/words/service/VideoAssetServiceTest.java`

**Interfaces:**
- Produces: `VideoAssetService.publish(Long id): VideoResponse`
- Produces: `VideoAssetService.unpublish(Long id): VideoResponse`
- Produces: `VideoResponse.publishStatus`, `publishedAt`, `unpublishedAt`

- [ ] **Step 1: Write failing tests**

Add service tests that assert upload defaults to `UNPUBLISHED`, publishing requires a ready URL, and an owner teacher can publish only their own teacher-scoped video.

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=VideoAssetServiceTest`
Expected: FAIL because `VideoPublishStatus`, `publish`, and `unpublish` do not exist.

- [ ] **Step 3: Add model and migration**

Create enum:

```java
package com.example.words.model;

public enum VideoPublishStatus {
    UNPUBLISHED,
    PUBLISHED
}
```

Add columns to `VideoAsset`: `publishStatus`, `publishedAt`, `unpublishedAt`.

Create Flyway migration with:

```sql
ALTER TABLE video_assets
    ADD COLUMN IF NOT EXISTS publish_status VARCHAR(20) NOT NULL DEFAULT 'UNPUBLISHED';

ALTER TABLE video_assets
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;

ALTER TABLE video_assets
    ADD COLUMN IF NOT EXISTS unpublished_at TIMESTAMP;

ALTER TABLE video_assets
    ADD CONSTRAINT ck_video_assets_publish_status
    CHECK (publish_status IN ('UNPUBLISHED', 'PUBLISHED'));

CREATE INDEX IF NOT EXISTS idx_video_assets_publish_status
    ON video_assets(publish_status);

CREATE INDEX IF NOT EXISTS idx_video_assets_student_visible
    ON video_assets(publish_status, status, updated_at DESC);
```

- [ ] **Step 4: Implement manager service/controller changes**

Add `publish` and `unpublish` methods. `publish` checks manager permission, `READY`, and non-blank `mediaUrl`; `unpublish` only checks manager permission. Both are idempotent.

- [ ] **Step 5: Run backend tests**

Run: `./mvnw test -Dtest=VideoAssetServiceTest`
Expected: PASS.

### Task 2: Student Video APIs

**Files:**
- Create: `src/main/java/com/example/words/dto/StudentVideoResponse.java`
- Modify: `src/main/java/com/example/words/model/VideoAccessMode.java`
- Modify: `src/main/java/com/example/words/service/TeacherStudentService.java`
- Modify: `src/main/java/com/example/words/service/VideoAssetService.java`
- Modify: `src/main/java/com/example/words/controller/StudentController.java`
- Test: `src/test/java/com/example/words/service/VideoAssetServiceTest.java`

**Interfaces:**
- Produces: `VideoAssetService.listStudentVideosPage(int page, int size, String keyword): Page<StudentVideoResponse>`
- Produces: `VideoAssetService.getStudentPlayback(Long id): VideoAccessResponse`
- Produces: `TeacherStudentService.isStudentVisibleToTeacher(Long studentId, Long teacherId): boolean`
- Produces: `VideoAccessMode.PLAY`

- [ ] **Step 1: Write failing student visibility tests**

Cover system published videos, teacher published videos for responsible students, and unpublished videos being hidden.

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=VideoAssetServiceTest`
Expected: FAIL because student APIs do not exist.

- [ ] **Step 3: Add student-facing DTO and service methods**

`StudentVideoResponse` contains `id`, `title`, `description`, `coverUrl`, `durationSeconds`, `createdByDisplayName`, `publishedAt`, and `updatedAt`.

Student listing uses a JPA Specification:

```java
status = READY
publishStatus = PUBLISHED
scopeType = SYSTEM OR ownerUserId IN responsibleTeacherIds
```

- [ ] **Step 4: Add controller endpoints**

Add to `StudentController`:

```java
@GetMapping("/me/videos/page")
@PreAuthorize("hasRole('STUDENT')")
public ResponseEntity<Page<StudentVideoResponse>> getMyVideos(...)

@GetMapping("/me/videos/{id}/play")
@PreAuthorize("hasRole('STUDENT')")
public ResponseEntity<VideoAccessResponse> playVideo(@PathVariable Long id)
```

- [ ] **Step 5: Run backend tests**

Run: `./mvnw test -Dtest=VideoAssetServiceTest`
Expected: PASS.

### Task 3: Admin Frontend Publishing Controls

**Files:**
- Modify: `frontend/admin/src/types/api.ts`
- Modify: `frontend/admin/src/lib/api.ts`
- Modify: `frontend/admin/src/pages/videos-page.tsx`

**Interfaces:**
- Consumes: `VideoResponse.publishStatus`, `publishedAt`, `unpublishedAt`
- Consumes: `POST /api/videos/{id}/publish`
- Consumes: `POST /api/videos/{id}/unpublish`

- [ ] **Step 1: Update admin frontend types and API client**

Add `VideoPublishStatus = "UNPUBLISHED" | "PUBLISHED"`, response fields, `publishVideo`, and `unpublishVideo`.

- [ ] **Step 2: Add UI controls**

Display publish badge, publish filter, and publish/unpublish buttons. Disable publish for non-previewable videos.

- [ ] **Step 3: Build admin frontend**

Run: `npm run build --workspace frontend/admin` if workspaces are configured; otherwise run `npm run build` from `frontend/admin`.
Expected: PASS.

### Task 4: Student Frontend Video Tab

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/index.ts`
- Create: `frontend/src/student/StudentVideos.tsx`
- Modify: `frontend/src/student/StudentWorkspace.tsx`
- Modify: `frontend/src/student/student-workspace.css`

**Interfaces:**
- Consumes: `GET /api/students/me/videos/page`
- Consumes: `GET /api/students/me/videos/{id}/play`

- [ ] **Step 1: Add student video types and API client**

Create `StudentVideo`, `StudentVideoPage`, `VideoAccessResponse`, and `studentVideoApi`.

- [ ] **Step 2: Build the student video tab**

Add a `视频` tab with a video list, empty/loading/error states, and a player overlay that requests playback on demand.

- [ ] **Step 3: Build unified frontend**

Run: `npm run build` from `frontend`.
Expected: PASS.

### Task 5: Full Verification and Docker Restart

**Files:**
- No new files.

**Interfaces:**
- Verifies all previous tasks.

- [ ] **Step 1: Run backend verification**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 2: Run frontend build**

Run: `npm run build` from `frontend`.
Expected: PASS.

- [ ] **Step 3: Rebuild frontend container**

Run: `docker-compose up -d --build frontend`
Expected: Docker rebuild succeeds and frontend container restarts.
