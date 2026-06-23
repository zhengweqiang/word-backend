# Video Storage Provider Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved Provider Adapter design so the current video backend can use either Tencent VOD or Volcengine VOD from the existing admin upload, sync, preview, and delete flows.

**Architecture:** Keep `VideoAssetService` as the business workflow and move cloud-specific behavior behind `VideoStorageGateway` adapters selected by `VideoStorageGatewayRegistry`. Add provider metadata to `VideoStorageConfig`, preserve legacy Tencent data through defaults, and keep the admin video resource page largely unchanged.

**Tech Stack:** Spring Boot 3.1.8, Java 17, Spring Data JPA, Flyway, JUnit 5, Mockito, Solid admin frontend, TypeScript.

## Global Constraints

- No large display-layer redesign.
- Existing Tencent VOD configs and videos must continue to work.
- Preserve `PROCESSING / READY / FAILED` video status semantics.
- Do not introduce browser direct upload, upload sessions, publish/unpublish, student video playback, callbacks, or audit logs in this implementation.
- Keep cloud provider differences inside provider gateway implementations.

---

### Task 1: Provider Model And Persistence

**Files:**
- Create: `src/main/java/com/example/words/model/VideoStorageProviderType.java`
- Create: `src/main/resources/db/migration/V29__add_video_storage_provider_type.sql`
- Modify: `src/main/java/com/example/words/model/VideoStorageConfig.java`
- Modify: `src/main/java/com/example/words/dto/CreateVideoStorageConfigRequest.java`
- Modify: `src/main/java/com/example/words/dto/UpdateVideoStorageConfigRequest.java`
- Modify: `src/main/java/com/example/words/dto/VideoStorageConfigResponse.java`
- Test: `src/test/java/com/example/words/dto/VideoStorageConfigRequestValidationTest.java`

**Interfaces:**
- Produces: `VideoStorageProviderType` enum with `TENCENT_VOD` and `VOLCENGINE_VOD`.
- Produces: `VideoStorageConfig.getProviderType()` and `getSpaceName()`.

- [ ] Add failing DTO validation tests for required `providerType` and optional `spaceName`.
- [ ] Run the DTO validation test and confirm it fails because the fields are missing.
- [ ] Add enum, entity fields, DTO fields, response mapping, and Flyway migration.
- [ ] Run the DTO validation test and confirm it passes.

### Task 2: Provider Gateway Abstraction

**Files:**
- Create: `src/main/java/com/example/words/service/video/VideoStorageGateway.java`
- Create: `src/main/java/com/example/words/service/video/VideoStorageGatewayRegistry.java`
- Create: `src/main/java/com/example/words/service/video/VideoUploadResult.java`
- Create: `src/main/java/com/example/words/service/video/VideoMediaInfo.java`
- Test: `src/test/java/com/example/words/service/video/VideoStorageGatewayRegistryTest.java`

**Interfaces:**
- Consumes: `VideoStorageProviderType`.
- Produces: registry lookup by provider type.

- [ ] Add failing registry tests for successful lookup and unsupported lookup.
- [ ] Run the registry test and confirm it fails because the abstraction does not exist.
- [ ] Add gateway interface, records, and registry implementation.
- [ ] Run the registry test and confirm it passes.

### Task 3: Tencent Adapter And Business Wiring

**Files:**
- Create: `src/main/java/com/example/words/service/video/TencentVodStorageGateway.java`
- Modify: `src/main/java/com/example/words/service/VideoAssetService.java`
- Modify: `src/main/java/com/example/words/service/VideoStorageConfigService.java`
- Modify: `src/test/java/com/example/words/service/VideoAssetServiceTest.java`
- Modify: `src/test/java/com/example/words/service/VideoStorageConfigServiceTest.java`

**Interfaces:**
- Consumes: `VideoStorageGatewayRegistry`.
- Produces: `VideoAssetService` selects gateway from the video's storage config provider.

- [ ] Add failing service tests proving upload, sync, delete, and config test select the gateway matching `providerType`.
- [ ] Run targeted service tests and confirm they fail because services still depend on `TencentVodGateway`.
- [ ] Implement `TencentVodStorageGateway` by moving current Tencent SDK behavior to the unified gateway interface.
- [ ] Rewire `VideoAssetService` and `VideoStorageConfigService` to use `VideoStorageGatewayRegistry`.
- [ ] Run targeted service tests and confirm they pass.

### Task 4: Volcengine Adapter Skeleton

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/example/words/service/video/VolcengineVodStorageGateway.java`
- Test: `src/test/java/com/example/words/service/video/VolcengineVodStorageGatewayTest.java`

**Interfaces:**
- Consumes: `VideoStorageConfig` provider fields.
- Produces: `providerType() == VOLCENGINE_VOD` and validated config requirements.

- [ ] Add failing unit tests for provider type and missing `spaceName` validation.
- [ ] Run the tests and confirm they fail because the adapter does not exist.
- [ ] Add Volcengine SDK dependency and adapter implementation using reflection-based SDK calls where practical.
- [ ] Run adapter tests and compile to confirm the code is valid.

### Task 5: Admin Frontend Minimal Provider Fields

**Files:**
- Modify: `frontend/admin/src/types/api.ts`
- Modify: `frontend/admin/src/pages/video-storage-page.tsx`
- Modify: `frontend/admin/src/pages/videos-page.tsx`

**Interfaces:**
- Consumes: backend `providerType` and `spaceName` fields.
- Produces: admin config form can submit Tencent or Volcengine provider config.

- [ ] Add provider fields to TypeScript API types.
- [ ] Add provider select and `spaceName` field shown only for Volcengine.
- [ ] Adjust visible labels for Tencent vs Volcengine credentials.
- [ ] Change video page copy from “腾讯云状态” to “云端状态”.
- [ ] Run frontend checks available in `frontend/admin/package.json`.

### Task 6: Final Verification

**Files:**
- Verify all changed files.

**Interfaces:**
- Confirms implementation meets the approved design.

- [ ] Run `./mvnw test`.
- [ ] Run admin frontend test/build command available in `frontend/admin/package.json`.
- [ ] Run `git status --short` and inspect the diff.
- [ ] Check requirements against the design document.
