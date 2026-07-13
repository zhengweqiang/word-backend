# Video Upload Auto Ready and Published Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make successful ADMIN and TEACHER video uploads immediately appear as previewable and published, so teachers can view administrator system videos in the video library.

**Architecture:** Keep the existing SYSTEM and TEACHER resource scopes and teacher visibility predicate. Change only the successful upload persistence path in VideoAssetService to set the new asset's local availability fields to READY and PUBLISHED; the existing predicate then exposes administrator system videos to teachers without granting management privileges.

**Tech Stack:** Java 17, Spring Boot, JPA Specifications, JUnit 5, Mockito, Maven, Docker Compose.

---

### Task 1: Lock the upload defaults with failing service tests

**Files:**
- Modify: `src/test/java/com/example/words/service/VideoAssetServiceTest.java:128-177`
- Test: `src/test/java/com/example/words/service/VideoAssetServiceTest.java`

- [ ] **Step 1: Add two failing tests for cloud-processing upload metadata**

Add these tests after `uploadShouldSaveTeacherVideoAndReturnPreviewableResponse`. In both tests, mock `describeMedia` with `ready=false`, `preferredPlaybackReady=false`, and `cloudPublished=false`, while retaining the upload result's media URL.

~~~java
@Test
void uploadShouldMarkTeacherVideoReadyAndPublishedWhenCloudReportsProcessing() {
    AppUser actor = teacher();
    defaultStorageConfig = defaultConfig();
    authenticate(actor);
    stubUploadWithProcessingCloudMetadata(defaultStorageConfig, actor);

    VideoResponse response = videoAssetService.upload(videoFile(), "Teacher lesson", "Description");

    assertEquals(ResourceScopeType.TEACHER, response.getScopeType());
    assertEquals(VideoStatus.READY, response.getStatus());
    assertEquals(VideoCloudPublishStatus.PUBLISHED, response.getCloudPublishStatus());
    assertTrue(response.getPublishedAt() != null);
    assertTrue(response.isCanManage());
}

@Test
void uploadShouldMarkAdminSystemVideoReadyAndPublishedWhenCloudReportsProcessing() {
    AppUser actor = admin();
    defaultStorageConfig = defaultConfig();
    authenticate(actor);
    stubUploadWithProcessingCloudMetadata(defaultStorageConfig, actor);

    VideoResponse response = videoAssetService.upload(videoFile(), "Admin lesson", "Description");

    assertEquals(ResourceScopeType.SYSTEM, response.getScopeType());
    assertEquals(VideoStatus.READY, response.getStatus());
    assertEquals(VideoCloudPublishStatus.PUBLISHED, response.getCloudPublishStatus());
    assertTrue(response.getPublishedAt() != null);
    assertTrue(response.isCanManage());
}
~~~

Add these test helpers:

~~~java
private MockMultipartFile videoFile() {
    return new MockMultipartFile("file", "lesson.mp4", "video/mp4", "video-bytes".getBytes());
}

private void stubUploadWithProcessingCloudMetadata(VideoStorageConfig config, AppUser actor) {
    when(volcengineStorageGateway.upload(any(), any(), any(), any(), any())).thenReturn(
            new VideoUploadResult("file-123", "https://vod.example.com/lesson.mp4",
                    "https://vod.example.com/cover.jpg", false)
    );
    when(volcengineStorageGateway.describeMedia(config, "file-123")).thenReturn(
            new VideoMediaInfo("file-123", "https://vod.example.com/lesson.mp4",
                    "https://vod.example.com/cover.jpg", 120L, false, false, false, null)
    );
    when(videoAssetRepository.save(any(VideoAsset.class))).thenAnswer(invocation -> {
        VideoAsset asset = invocation.getArgument(0);
        asset.setId(55L);
        return asset;
    });
    when(appUserRepository.findAllById(any())).thenReturn(List.of(actor));
    when(videoStorageConfigRepository.findAllById(any())).thenReturn(List.of(config));
}
~~~

- [ ] **Step 2: Run the focused test class and verify both new tests fail**

Run:

~~~powershell
.\mvnw test "-Dtest=VideoAssetServiceTest"
~~~

Expected: the two new tests fail because the current upload path derives `PROCESSING` and `UNPUBLISHED` from the mocked cloud metadata.

### Task 2: Persist the approved default states on successful upload

**Files:**
- Modify: `src/main/java/com/example/words/service/VideoAssetService.java:143-152`
- Test: `src/test/java/com/example/words/service/VideoAssetServiceTest.java`

- [ ] **Step 1: Replace cloud-derived upload defaults with the approved local defaults**

In `VideoAssetService.upload`, after setting the media URL, cover URL, and duration, replace the existing `resolveStatus(...)` and `syncPublishStatusFromCloud(...)` calls with:

~~~java
LocalDateTime uploadedAt = LocalDateTime.now();
videoAsset.setStatus(VideoStatus.READY);
videoAsset.setCloudPublishStatus(VideoCloudPublishStatus.PUBLISHED);
videoAsset.setPublishedAt(uploadedAt);
videoAsset.setUnpublishedAt(null);
~~~

Keep scope assignment and exception handling unchanged. This executes only after cloud upload and media lookup succeed.

- [ ] **Step 2: Run the focused test class and verify it passes**

Run:

~~~powershell
.\mvnw test "-Dtest=VideoAssetServiceTest"
~~~

Expected: all `VideoAssetServiceTest` tests pass.

- [ ] **Step 3: Run the complete backend test suite**

Run:

~~~powershell
.\mvnw test
~~~

Expected: all backend tests pass.

- [ ] **Step 4: Rebuild and restart the backend container, then verify health**

Run:

~~~powershell
docker compose up -d --build app
docker compose ps
~~~

Expected: `words-app` reports `healthy`; `words-db` and `words-frontend` remain running.

- [ ] **Step 5: Manually verify the role workflow**

1. Sign in as ADMIN at `http://localhost:8083/admin/login` and upload a video.
2. Confirm its card shows “可预览” and “已发布”.
3. Sign in as a TEACHER and open `http://localhost:8083/admin/videos`.
4. Confirm the ADMIN video is present and can be previewed, while its management actions remain disabled for the teacher.

- [ ] **Step 6: Commit the implementation**

~~~powershell
git add src/main/java/com/example/words/service/VideoAssetService.java src/test/java/com/example/words/service/VideoAssetServiceTest.java
git commit -m "fix: publish uploaded videos immediately"
~~~
