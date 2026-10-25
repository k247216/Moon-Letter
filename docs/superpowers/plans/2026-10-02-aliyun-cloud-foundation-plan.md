# 阿里云低成本云端基础 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a vendor-configurable production foundation for Alibaba Cloud ECS + OSS while preserving local MinIO/local-storage development and the existing Android media contract.

**Architecture:** Keep `ObjectStorage` as the server boundary. Implement a real S3-compatible adapter with AWS SDK for Java 2.x, use short-lived signed PUT URLs for media uploads, and keep authorization/metadata/completion in Spring Boot. Run PostgreSQL on the first ECS phase with explicit OSS backups, leaving `JDBC_DATABASE_URL` as the RDS migration seam.

**Tech Stack:** Spring Boot 3.5, Java 21, PostgreSQL/Flyway, AWS SDK for Java 2.x S3 client/presigner, MinIO for local integration tests, Android Kotlin/OkHttp, Docker Compose, PowerShell and Bash backup helpers.

**Spec:** `docs/superpowers/specs/2026-10-02-aliyun-cloud-foundation-design.md`

## Global Constraints

- The Android app never contains long-lived OSS credentials.
- The OSS bucket is private; member authorization stays in the Spring Boot API.
- Local storage and local MinIO remain supported for development and tests.
- Media is not considered deliverable until server-side checksum and byte-size validation succeeds.
- First production phase uses self-hosted PostgreSQL on ECS plus OSS backups; RDS is a later connection-string migration.
- No cloud account purchase, user authentication, domain registration, or external deployment is performed by this task.

## Review Focus

- A missing S3 configuration must fail fast rather than silently writing to local disk.
- A signed PUT URL must be short-lived and bound to the expected object and MIME type.
- An upload with the wrong checksum or size must remain unavailable to the partner.
- A member from another couple must not read a valid asset ID.
- A failed backup must never be reported as a successful cloud backup.

---

### Task 1: S3 adapter configuration and signing

**Files:**
- Modify: `server/pom.xml`
- Modify: `server/src/main/resources/application.yml`
- Modify: `server/src/main/java/com/twomemory/app/media/ObjectStorage.java`
- Modify: `server/src/main/java/com/twomemory/app/media/S3ObjectStorage.java`
- Create: `server/src/main/java/com/twomemory/app/media/S3StorageProperties.java`
- Test: `server/src/test/java/com/twomemory/app/media/S3ObjectStorageTest.java`

**Interfaces:**
- Consumes: `CreateMediaCommand`, existing `ObjectStorage`, `MediaService`.
- Produces: a real S3-compatible `ObjectStorage` implementation with `presignPut`, `head`, and private-object read operations.

- [ ] **Step 1: Write the failing adapter tests**
  - Assert missing endpoint/bucket/region/access key/secret key is rejected during property validation.
  - Assert the generated PUT URL contains the configured bucket/object key and has an expiration no later than 15 minutes.
  - Assert `head` delegates to the S3 client and returns object size/checksum metadata.
- [ ] **Step 2: Run the focused server test and verify it fails for the missing adapter/configuration behavior**
- [ ] **Step 3: Add the AWS SDK v2 BOM and S3 dependencies, implement validated properties and an endpoint-aware S3 client/presigner**
- [ ] **Step 4: Run the focused test and verify it passes**
- [ ] **Step 5: Run the existing media/service unit tests and verify they remain green**

### Task 2: Cloud media upload and private reads

**Files:**
- Modify: `server/src/main/java/com/twomemory/app/media/MediaController.java`
- Modify: `server/src/main/java/com/twomemory/app/media/MediaService.java`
- Modify: `server/src/main/java/com/twomemory/app/media/MediaDtos.java`
- Create/Modify: `server/src/test/java/com/twomemory/app/media/MediaCloudFlowTest.java`

**Interfaces:**
- Consumes: Task 1 `ObjectStorage` operations and existing `UploadTicket`/asset state model.
- Produces: a storage-mode-independent upload completion flow; local mode keeps `/data`, S3 mode uses the signed URL and server-side `head`.

- [ ] **Step 1: Write failing tests for S3-mode ticket completion, checksum mismatch, and private member read authorization**
- [ ] **Step 2: Run the focused test and verify it fails because S3 mode is currently a placeholder/local-only path**
- [ ] **Step 3: Implement S3-mode completion and private reads without weakening the existing couple access policy**
- [ ] **Step 4: Run the focused test and verify it passes**
- [ ] **Step 5: Run the complete server media/auth test slice and verify it passes**

### Task 3: Android direct media upload using signed tickets

**Files:**
- Modify: `android/app/src/main/java/com/twomemory/app/MediaUploadManager.kt`
- Modify: `android/core/network/src/main/java/com/twomemory/network/MediaApi.kt`
- Test: `android/core/network/src/test/java/com/twomemory/network/MediaUploadRequestTest.kt`
- Test: `android/app/src/test/java/com/twomemory/app/MediaUploadManagerTest.kt`

**Interfaces:**
- Consumes: Task 2 `UploadTicketDto.url` and existing asset completion endpoint.
- Produces: Android uploads media to the ticket URL when present, then completes through the API; local mode remains compatible.

- [ ] **Step 1: Write failing tests for PUT-to-ticket and completion ordering**
- [ ] **Step 2: Run focused tests and verify they fail because the client currently ignores `UploadTicketDto.url` and always calls `/data`**
- [ ] **Step 3: Implement the signed PUT path with required MIME/length headers and keep the local fallback only for local tickets**
- [ ] **Step 4: Run focused Android JVM tests and verify they pass**
- [ ] **Step 5: Run `:app:testDebugUnitTest` and verify the complete Android JVM suite passes**

### Task 4: ECS/OSS production configuration and backup tooling

**Files:**
- Modify: `server/src/main/resources/application.yml`
- Create: `infra/production-compose.yaml`
- Create: `infra/production.env.example`
- Create: `scripts/backup-postgres-to-oss.sh`
- Create: `scripts/restore-postgres-from-backup.sh`
- Modify: `README.md`
- Create: `docs/deployment/alibaba-cloud-evolution.md`
- Test: `scripts/backup-config-test.ps1`

**Interfaces:**
- Consumes: Task 1 storage environment contract and current Docker PostgreSQL layout.
- Produces: a documented ECS deployment shape, secret-free templates, scheduled backup commands, and RDS migration notes.

- [ ] **Step 1: Write a failing configuration test that rejects committed real secrets and requires the named production variables**
- [ ] **Step 2: Run it and verify it fails before the templates/scripts exist**
- [ ] **Step 3: Add the production templates and backup/restore scripts with explicit dry-run and failure behavior**
- [ ] **Step 4: Run the configuration test and verify it passes**
- [ ] **Step 5: Run `git diff --check` and validate Docker Compose syntax without starting production services**

### Task 5: End-to-end verification and handoff

**Files:**
- Modify: `docs/testing/real-use-log.md`
- Modify: `docs/handoff/2026-10-02-takeover-status-and-pitfalls.md`
- Modify: `.superpowers/sdd/aliyun-cloud-foundation/progress.md`

- [ ] **Step 1: Run the complete server test slice, Android JVM suite, and Android debug build**
- [ ] **Step 2: Verify the APK has no OSS/database credential strings**
- [ ] **Step 3: Record the exact commands, limitations, and deployment inputs still required from the account owner**
- [ ] **Step 4: Build a fresh uniquely named debug APK only if the Android client changed**
