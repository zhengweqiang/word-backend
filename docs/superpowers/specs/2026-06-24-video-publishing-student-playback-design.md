# Video Publishing and Student Playback Design

## Purpose

Migrate the useful parts of VidFlow's publishing and playback model into the word learning app without turning the project into a standalone video platform. Teaching videos should keep their existing cloud processing state while gaining a separate publication state that controls whether students can see and play them.

## Scope

This design covers the first migration slice:

- Admins and owning teachers can publish and unpublish ready teaching videos.
- Students can list and play only videos that are ready, published, and visible to them.
- The admin video page shows publication state and exposes publish/unpublish actions.
- The student workspace gains a video entry for published learning videos.

This design does not cover browser direct upload, upload sessions, VOD callback processing, video audit logs, video review workflow, or playback URL signing. Those remain later migration candidates from VidFlow.

## Data Model

Keep `VideoStatus` focused on cloud media readiness:

- `PROCESSING`: cloud media exists but a playable URL is not ready.
- `READY`: cloud media has a playable URL.
- `FAILED`: upload or media processing failed.

Add a separate `VideoPublishStatus`:

- `UNPUBLISHED`: default for all existing and newly uploaded videos.
- `PUBLISHED`: students may see and play the video if all visibility rules pass.

Add nullable `published_at` and `unpublished_at` timestamps to `video_assets`. These timestamps support sorting, operational clarity, and future audit work without requiring a full audit-log table in this slice.

## Visibility Rules

Managers keep the existing rules:

- Admins can view and manage all videos.
- Teachers can view system videos and their own teacher-scoped videos.
- Teachers can manage only their own teacher-scoped videos.

Students use a new read-only visibility path:

- System videos are visible when `status = READY` and `publish_status = PUBLISHED`.
- Teacher videos are visible when `status = READY`, `publish_status = PUBLISHED`, and the video's owner teacher is responsible for the student through direct teacher-student assignment or classroom membership.

Students must not use the manager `/api/videos/**` endpoints.

## Backend API

Extend manager video endpoints:

- `POST /api/videos/{id}/publish`
- `POST /api/videos/{id}/unpublish`

Publish requires:

- Current user can manage the video.
- Video status is `READY`.
- Video has a non-blank playable media URL.

Unpublish requires:

- Current user can manage the video.

Add student video endpoints:

- `GET /api/students/me/videos/page?page=1&size=12&keyword=...`
- `GET /api/students/me/videos/{id}/play`

Student list returns video cards without exposing manager-only fields such as storage config ID or raw cloud media ID unless they are already part of the public response. Student play returns an access response containing `videoId`, `mode = PLAY`, `url`, and `coverUrl`.

## Frontend

Admin frontend:

- Add publication state to video cards.
- Add a publish status filter.
- Add publish/unpublish buttons beside preview, sync, and delete.
- Disable publish while a video is not previewable.

Student frontend:

- Add a `视频` tab or equivalent first-class student workspace entry.
- List visible published videos with title, description, cover, duration, and publication date.
- Open a video player overlay or inline player after requesting the student play endpoint.
- Show empty, loading, and error states consistent with the existing student workspace.

## Error Handling

- Publishing a non-ready video returns a bad request with a clear message.
- Student requests for unpublished or unauthorized videos return not found or access denied through the existing exception handling pattern.
- Student playback for a video missing a playable URL returns a bad request saying the video is not ready for playback.
- Repeated publish/unpublish calls are idempotent at the service level.

## Testing

Backend tests should cover:

- Newly uploaded videos default to `UNPUBLISHED`.
- Admin can publish ready videos.
- Owner teacher can publish their own teacher-scoped ready videos.
- Teacher cannot publish system videos or another teacher's videos.
- Non-ready videos cannot be published.
- Students list system published videos.
- Students list teacher videos only when the owner teacher is responsible for them.
- Students cannot play unpublished videos.

Frontend verification should cover:

- Admin video page builds with the new fields and actions.
- Student workspace builds with the new video tab.
- Docker frontend container is rebuilt after frontend changes.

## Migration

Add a Flyway migration that:

- Creates the `publish_status` column on `video_assets` with default `UNPUBLISHED`.
- Creates `published_at` and `unpublished_at`.
- Adds a check constraint for `UNPUBLISHED` and `PUBLISHED`.
- Adds indexes for student listing by publication and updated time.

Existing rows remain unpublished so no video becomes student-visible without an explicit manager action.
