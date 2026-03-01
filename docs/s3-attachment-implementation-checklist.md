# Secure Support Hub Codebase Work Checklist (Phase 2 Attachments + Alignment)

**Project**: Secure Support Hub  
**Audience**: Full-stack implementation and review (you + Gemini + Codex)  
**Last updated**: 2026-03-01

---

## How to use this document

- This is the master execution checklist after the AWS/Fargate MVP.
- It incorporates confirmed product decisions and items learned from `docs/s3-attachment-implementation-checklist.md`.
- Each checkbox is a merge gate.
- Keep code, OpenAPI, README, and runbooks synchronized at each phase boundary.

---

## Confirmed Decisions (Locked on 2026-03-01)

- [x] Upload strategy: browser direct upload to S3 via pre-signed URL.
- [x] Attachment scope: support both request-level and comment-level attachments.
- [x] Delete behavior: attachments should be deleted together when parent request/comment is deleted.
- [x] Malware scanning: not in MVP scope.
- [x] Pre-signed flow pattern: `upload-url` + client direct `PUT` + backend `confirm` endpoint.

### Adopted default policy (industry-common, pragmatic MVP)

- [x] Max file size: `10 MiB` per file.
- [x] Allowed MIME types (safe business set): `image/jpeg`, `image/png`, `image/webp`, `application/pdf`, `text/plain`, `text/csv`.
- [x] Attachment count limits: up to `10` files per request body, up to `5` files per comment.
- [x] Pre-signed URL TTL: upload URL `5 min`, download URL `5 min`.
- [ ] Optional follow-up decision: move to `25 MiB` if business scenarios require larger logs/screenshots.

---

## Current Snapshot (What Exists vs Missing)

### Product

- [x] JWT login and RBAC (`USER`, `TRIAGE`, `ADMIN`).
- [x] Support request create/list/detail/update.
- [x] Request comments create/list.
- [x] Attachments are implemented in backend + frontend; OpenAPI updates are still pending.
- [x] Request delete endpoint is implemented.
- [x] Comment delete endpoint is implemented.
- [ ] AI-assist endpoints/UI are not implemented (README mentions them).

### Platform and delivery

- [x] AWS dev runtime: ECS Fargate + ALB + RDS + ECR + Terraform.
- [x] CI/CD pipelines exist (backend, frontend, deploy, terraform).
- [ ] Remaining unchecked controls in `docs/aws-fargate-cicd-checklist.md` still need closure.

### Docs and contract consistency

- [x] `docs/api/openapi.yaml` does not fully match runtime DTO shapes.
- [x] README roadmap/feature status has attachment phase inconsistency (`Phase 2` vs `Phase 3`).
- [ ] README documents AI endpoints that are not implemented.
- [x] Deployment runbook is aligned with Fargate-only runtime operations.

---

## Phase 1: Terraform and AWS Foundation for Attachments

- [x] Create attachment storage module (for example `infra/terraform/modules/s3_attachments`).
- [x] Create S3 bucket with:
  - block public access (all `true`)
  - default encryption (SSE-S3 for MVP; KMS optional follow-up)
  - versioning enabled
  - bucket owner enforced object ownership
- [x] Add lifecycle rules (abort incomplete multipart uploads, optional retention for non-current versions).
- [x] Configure CORS for frontend origin and required methods/headers only.
- [x] Update ECS task role IAM permissions (least privilege):
  - `s3:PutObject`
  - `s3:GetObject`
  - `s3:DeleteObject`
  - `s3:ListBucket` (only if required by implementation)
- [x] Ensure all S3 IAM permissions are strictly resource-scoped to the attachment bucket ARN/prefix (no wildcard `*` resource grants).
- [x] Wire bucket env vars into API task definition:
  - `AWS_S3_ATTACHMENT_BUCKET_NAME`
  - `AWS_REGION`
  - optional `AWS_S3_ENDPOINT` for LocalStack/local
- [x] Export bucket outputs in `infra/terraform/envs/dev/outputs.tf`.
- [x] Split GitHub OIDC roles into two service accounts:
  - Terraform role: broader IaC permissions (including S3 bucket configuration reads such as `GetReplicationConfiguration`)
  - CD deploy role: reduced to ECR/ECS deploy permissions only
- [x] Apply Terraform in `dev` and verify bucket policy/CORS/IAM.

---

## Phase 2: Backend Domain, Data Model, and API (Spring Boot)

### 2.1 Data model and migration

- [x] Add Flyway migration for attachment metadata table.
- [x] Support both parent types with explicit model: `request_id` nullable + `comment_id` nullable + check constraint "exactly one present"
- [x] Define concrete attachment columns (adapt to selected model), including:
  - `id`
  - `request_id` and/or `comment_id` (or `parent_type` + `parent_id`)
  - `file_name`
  - `content_type`
  - `file_size`
  - `s3_object_key`
  - `state` (`PENDING`, `ACTIVE`, `FAILED`)
  - `uploaded_by`
  - `created_at`
- [x] Track attachment lifecycle state (`PENDING`, `ACTIVE`, `FAILED`) to support upload confirm flow.
- [x] Track who uploaded and timestamps.
- [x] Define indexes for request/comment lookup and cleanup jobs.

### 2.2 S3 integration

- [x] Add AWS SDK v2 dependencies (`s3`, `s3-presigner`) in `apps/api/build.gradle.kts` (`s3` module provides `S3Presigner`).
- [x] Add S3 client/presigner config (AWS runtime and optional LocalStack endpoint override).
- [x] Create explicit Spring beans for `S3Client` and `S3Presigner` (AWS SDK v2), with endpoint override for local profile/testing.
- [x] Define deterministic object key format:
  - `requests/{requestId}/attachments/{attachmentId}/{sanitizedFilename}`
  - `requests/{requestId}/comments/{commentId}/attachments/{attachmentId}/{sanitizedFilename}`
- [x] Enforce server-side validation before issuing pre-signed URL:
  - MIME allowlist
  - max size (`10 MiB`)
  - parent ownership/authorization
  - count limits

### 2.3 Endpoints and RBAC

- [x] Implement request attachment endpoints:
  - `POST /api/v1/requests/{id}/attachments/upload-url`
  - `POST /api/v1/requests/{id}/attachments/{attachmentId}/confirm`
  - `GET /api/v1/requests/{id}/attachments`
  - `GET /api/v1/requests/{id}/attachments/{attachmentId}/download-url`
  - `DELETE /api/v1/requests/{id}/attachments/{attachmentId}`
- [x] Implement comment attachment endpoints with equivalent flow:
  - `POST /api/v1/requests/{requestId}/comments/{commentId}/attachments/upload-url`
  - `POST /api/v1/requests/{requestId}/comments/{commentId}/attachments/{attachmentId}/confirm`
  - `GET /api/v1/requests/{requestId}/comments/{commentId}/attachments`
  - `GET /api/v1/requests/{requestId}/comments/{commentId}/attachments/{attachmentId}/download-url`
  - `DELETE /api/v1/requests/{requestId}/comments/{commentId}/attachments/{attachmentId}`
- [x] RBAC rule implementation:
  - `USER`: only own request/comment thread attachments
  - `TRIAGE`/`ADMIN`: all attachments
- [x] Harden logging: never log pre-signed URLs, tokens, or sensitive object metadata.

### 2.4 Delete capabilities (newly added scope)

- [x] Add request delete endpoint (hard delete MVP):
  - `DELETE /api/v1/requests/{id}`
  - permission: owner or triage/admin
- [x] Add comment delete endpoint:
  - `DELETE /api/v1/requests/{requestId}/comments/{commentId}`
  - permission: comment author or triage/admin
- [x] Implement cascade attachment cleanup on delete:
  - delete attachment metadata rows
  - delete S3 objects (best-effort with retry or cleanup job)
- [x] Add cleanup strategy for orphaned `PENDING` uploads (scheduled task/TTL).

---

## Phase 3: Frontend UX and Flows (React)

- [x] Extend API client/types for request + comment attachment endpoints.
- [x] Implement uploader component:
  - drag-and-drop + file picker fallback
  - validation (size/type/count)
  - per-file progress state
  - retry/cancel
  - clear user-visible error states for S3 upload failures (including CORS/expired URL cases)
- [x] Implement request-level flow:
  - ask backend for upload URL
  - direct `PUT` to S3
  - call `confirm`
  - refresh attachment list
- [x] Implement comment-level flow using same orchestration.
- [x] Render attachment lists in request detail and comment cards.
- [x] Implement secure download interaction via backend-issued pre-signed URL.
- [x] Add delete controls for request/comment (once backend delete endpoints exist).

---

## Phase 4: Local Development, Testing, and CI

- [ ] Add optional LocalStack service in `infra/docker-compose/docker-compose.yml`.
- [ ] Add LocalStack init script to create test bucket automatically.
- [ ] Add local env docs/examples for S3 local endpoint and dummy credentials.
- [ ] Backend tests:
  - unit tests for validation/RBAC
  - integration tests for upload-url/confirm/download-url/delete
  - use Testcontainers + LocalStack module for S3 integration tests
- [ ] Frontend tests:
  - E2E upload/download/delete flows
  - E2E validation error cases (bad type, oversize, over-limit)
  - Playwright-based mocking strategy for pre-signed URL flows where direct S3 is unavailable in CI
- [ ] Ensure CI workflows include and pass new tests.

---

## Phase 5: OpenAPI, README, and Runbook Alignment

- [ ] Update `docs/api/openapi.yaml` with all new endpoints and schemas (attachments + delete endpoints).
- [ ] Reconcile OpenAPI with runtime DTOs for existing requests/comments endpoints.
- [ ] Update README:
  - mark attachment implementation status accurately
  - fix phase inconsistency for attachments
  - align AI-assist section with actual state
- [ ] Update deployment runbook for attachment env vars and S3 verification.
- [ ] Update incident runbook with attachment failure cases:
  - S3 CORS failure
  - pre-signed URL expired
  - confirm not called / pending orphan files

---

## Phase 6: Post-Attachment Backlog

- [ ] Implement AI assist endpoints/UI currently documented in README.
- [ ] Add auth rate limiting (documented but not implemented).
- [ ] Add explicit audit logging for sensitive actions.
- [ ] Close remaining unchecked security validation items in `docs/aws-fargate-cicd-checklist.md`.
- [ ] Add post-deploy smoke tests for new attachment and delete flows in CD.

---

## Definition of Done (Attachments + Delete Baseline)

- [ ] Request/comment attachments are fully functional with private S3 and RBAC.
- [ ] Request/comment delete endpoints are implemented and attachment cleanup is verified.
- [ ] API, web, CI tests, OpenAPI, README, and runbooks are all consistent.
- [ ] No plaintext secret leakage and no public S3 object exposure in the final setup.
