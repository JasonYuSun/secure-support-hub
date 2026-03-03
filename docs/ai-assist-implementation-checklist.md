# Secure Support Hub AI Assist Implementation Checklist

**Project**: Secure Support Hub  
**Audience**: Full-stack implementation and review
**Last updated**: 2026-03-03

---

## How to use this document

- This is the master execution checklist for implementing AI Assist after the completed S3 attachment phase.
- Each checkbox is a merge gate.
- Keep code, OpenAPI, README, E2E docs, and runbooks synchronized at each phase boundary.

---

## Confirmed Decisions (Locked on 2026-03-03)

- [x] Provider strategy: use provider-agnostic abstraction in codebase; run `stub` provider locally and `AWS Bedrock` provider in `dev`.
- [x] UX interaction model: three independent AI actions (not one combined action).
- [x] AI context includes attachment content parsing (not only request title/body/comments).
- [x] Attachment ingestion strategy (MVP): for Bedrock-capable multimodal models, pass attachment bytes (PDF/image) directly in model input; avoid custom OCR/PDF pipeline in MVP.
- [x] RBAC for AI assist:
  - `USER`: only on own requests
  - `TRIAGE` / `ADMIN`: any request
- [x] AI outputs must be persisted in database.
- [x] Draft response behavior: pre-fill comment input only; never auto-send.
- [x] Language behavior: response language follows user input language (Chinese/English auto-follow).
- [x] Security/cost hardening (PII redaction, quotas, strict throttling) is tracked as **Future Work**, not MVP gate.
- [x] Infrastructure principle: runtime config/permissions must be managed by IaC (Terraform) with no manual console drift.

---

## Current Snapshot (Codebase Reality)

- [x] Request/comment/attachment flows are implemented and passing E2E in current batch (`docs/ai-e2e/*`).
- [x] AI Assist core actions (`summarize` / `suggest-tags` / `draft-response`) are still not implemented in backend and frontend.
- [x] Tagging foundation is implemented (DB schema, backend APIs, frontend panel, OpenAPI, and E2E coverage).
- [x] README includes AI Assist in scope, but marks it as future phase.
- [x] Attachment metadata and S3 private object flow are available for building AI context from files.
- [x] Terraform code now includes AI runtime config (`ai_provider`, `ai_bedrock_model_id`) and Bedrock invoke permissions in ECS task role; apply/verification status is tracked in Phase 0.3.
- [x] Bedrock model-access toggle scripts exist in repo (`scripts/bedrock/enable-model-access.sh`, `scripts/bedrock/disable-model-access.sh`) and are documented in deployment runbook for ad hoc usage.

---

## Expected User Journeys (MVP Acceptance)

| Journey ID | Role                                       | Scenario                                 | Preconditions                                                       | Expected Result                                                                                                              | Priority |
| ---------- | ------------------------------------------ | ---------------------------------------- | ------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- | -------- |
| AJ-001     | USER                                       | Summarize own request                    | USER logged in and opens own `/requests/{id}`                       | Summary returns successfully and is shown in AI panel with metadata (`provider/model/time`).                                 | P0       |
| AJ-002     | USER                                       | Suggest and apply tags for own request   | USER logged in and request has meaningful description/comments      | Suggested tags are returned, user can apply selected tags, and applied tags persist after refresh.                           | P0       |
| AJ-003     | USER                                       | Draft response for own request           | USER logged in and opens own request detail                         | Draft is generated and clicking `Use Draft` fills comment textarea only (no auto-submit).                                    | P0       |
| AJ-004     | TRIAGE                                     | Generate AI outputs for any request      | TRIAGE logged in and opens request not created by self              | TRIAGE can run all 3 AI actions successfully on non-owned requests.                                                          | P0       |
| AJ-005     | ADMIN                                      | Generate AI outputs for any request      | ADMIN logged in and opens any request                               | ADMIN can run all 3 AI actions successfully.                                                                                 | P1       |
| AJ-006     | USER (negative)                            | Access control denial on others' request | USER logged in and tries AI action on request owned by another user | API returns `403`; UI shows permission error and no AI output is persisted for this user action.                             | P0       |
| AJ-007     | Any authorized role                        | Attachment-aware context generation      | Request includes text/csv/pdf/image attachments                     | AI output reflects attachment-derived context; parse failures are surfaced as partial-context notes without crashing action. | P1       |
| AJ-008     | Any authorized role                        | Chinese input language follow            | Request content and user prompt are Chinese                         | Returned summary/tags/draft are Chinese (or predominantly Chinese) with coherent wording.                                    | P1       |
| AJ-009     | Any authorized role                        | English input language follow            | Request content and user prompt are English                         | Returned summary/tags/draft are English with coherent wording.                                                               | P1       |
| AJ-010     | Any authorized role                        | Provider/runtime failure handling        | Provider timeout/error is simulated                                 | UI shows actionable retry message; API returns stable AI error code; run is persisted as `FAILED` with error metadata.       | P0       |
| AJ-011     | Any authorized role                        | Persistence/audit verification           | AI action executed successfully                                     | DB contains run record with request/action/provider/model/prompt version/input snapshot/output payload/latency/actor.        | P0       |
| AJ-012     | Any authorized role                        | Repeated-click idempotency behavior      | User triggers same action repeatedly in short window                | System avoids duplicate noisy writes or inconsistent output states based on chosen dedupe/idempotency strategy.              | P2       |
| AJ-013     | TRIAGE/ADMIN                               | Manage tag dictionary                    | TRIAGE or ADMIN logged in                                           | Can create/delete tags in dictionary according to governance rules.                                                          | P0       |
| AJ-014     | Any authenticated role with request access | Apply/unapply existing tag on request    | User can access target request                                      | Tag apply/unapply succeeds and remains consistent after refresh.                                                             | P0       |

---

## Tagging Feature Design (MVP Required)

### Design goals

- [x] Keep tag taxonomy globally reusable across requests.
- [x] Separate "dictionary management" from "request tagging" permissions.
- [x] Guarantee deterministic RBAC:
  - `TRIAGE`/`ADMIN`: add/delete tags in dictionary
  - all authenticated roles with request access (`USER`, `TRIAGE`, `ADMIN`): apply/unapply existing tags on request

### Data model design

- [x] `tags` table:
  - `id`
  - `name` (unique, case-insensitive normalized key)
  - `created_by`
  - `created_at`
  - optional `deleted_at` for soft-delete
- [x] `request_tags` table:
  - `request_id`
  - `tag_id`
  - `applied_by`
  - `applied_at`
  - unique constraint on (`request_id`, `tag_id`)
- [x] Define delete semantics for dictionary tags:
  - preferred MVP path: soft-delete dictionary tag and keep historical request_tag records
  - if hard-delete path is chosen, block delete when tag is in use (`409`) and require unapply first

### API contract design

- [x] Tag dictionary endpoints:
  - `GET /api/v1/tags` (authenticated users)
  - `POST /api/v1/tags` (`TRIAGE`/`ADMIN` only)
  - `DELETE /api/v1/tags/{tagId}` (`TRIAGE`/`ADMIN` only)
- [x] Request tagging endpoints:
  - `GET /api/v1/requests/{id}/tags` (roles with request read access)
  - `POST /api/v1/requests/{id}/tags/{tagId}` (roles with request access)
  - `DELETE /api/v1/requests/{id}/tags/{tagId}` (roles with request access)
- [x] AI integration contract:
  - `suggest-tags` returns existing tag IDs when confidently matched; otherwise returns candidate names with `isNew=true`
  - creating new dictionary tags from AI suggestions remains explicit user action (never auto-create)

---

## Phase 0: Cloud and Terraform Readiness (Bedrock Enablement)

Note:
- Treat Bedrock setup as **IaC-first**. Everything Terraform can manage must be in Terraform.
- For account-level model subscription/agreement steps that are not represented as stable Terraform resources, use scripted automation (CLI/SDK preflight) committed in repo and executed ad hoc by maintainers (or as explicit one-off bootstrap job), not manual console clicks.

### 0.1 AWS account and region prerequisites

- [x] Confirm target runtime region for AI is `ap-southeast-2` (same as current ECS runtime), or explicitly document cross-region inference strategy.
- [x] Add scripted Bedrock model-access bootstrap (CLI/SDK) under repo automation (`scripts/bedrock/enable-model-access.sh`), rather than relying on manual console clicks.
- [x] Ensure bootstrap handles provider-specific one-time prerequisites (for example Anthropic use-case submission) in an auditable way.
- [x] Ensure bootstrap handles third-party model agreement/subscription flow where required.
- [x] Add scripted Bedrock cost guard toggle for demo idle windows (`scripts/bedrock/disable-model-access.sh`) with runtime deny policy option.
- [x] Lock model choice for MVP and record canonical `modelId` used by backend config: `anthropic.claude-sonnet-4-6` (Claude Sonnet 4.6).

### 0.2 Terraform changes for ECS runtime

- [x] Add AI runtime variables to Terraform `ecs` module (minimum):
  - `ai_provider` (dev default `bedrock`)
  - `ai_bedrock_model_id`
  - optional flags for multimodal support/capability expectations
- [x] Wire those variables into API task definition environment variables.
- [x] Extend ECS task role IAM policy with Bedrock runtime permissions required by implementation:
  - `bedrock:InvokeModel`
  - `bedrock:InvokeModelWithResponseStream` (only if streaming path is used)
  - `bedrock:Converse`
  - `bedrock:ConverseStream` (only if converse streaming path is used)
- [x] Scope Bedrock permissions to approved model resources where feasible; if wildcard is required by API behavior, document and justify explicitly.
- [x] Update `infra/terraform/envs/dev/main.tf` to pass AI variables into module `ecs`.
- [x] Expose relevant AI runtime outputs (or document variable mapping) so deployment/runbook can be validated quickly.
- [x] Add explicit drift guard: no AI runtime/IAM changes are applied manually in console; all changes flow through Terraform plan/apply.

### 0.3 Apply and verify

- [x] Run `terraform plan` and `terraform apply` in `infra/terraform/envs/dev`.
- [x] Run Bedrock model-access bootstrap automation (`scripts/bedrock/enable-model-access.sh`) as a documented ad hoc infra bootstrap command and capture output artifact.
- [x] Verify applied ECS task definition contains AI env vars and no plaintext secrets.
- [x] Verify running ECS API task can call Bedrock model successfully with IAM role credentials (no static access keys).

---

## Phase 1: Architecture and Contract Baseline

### 1.1 API shape and action boundaries

- [x] Define three independent API actions:
  - `POST /api/v1/requests/{id}/ai/summarize`
  - `POST /api/v1/requests/{id}/ai/suggest-tags`
  - `POST /api/v1/requests/{id}/ai/draft-response`
- [x] Ensure each action has its own request/response DTO and validation (no overloaded "do everything" endpoint).
- [x] Finalize tagging API contracts from "Tagging Feature Design" and include them in OpenAPI-first schema review before implementation.
- [x] Add idempotency strategy for repeated clicks (for example client idempotency key or short dedupe window on same input hash).
- [x] Define consistent AI error codes in global API error model (`AI_PROVIDER_ERROR`, `AI_TIMEOUT`, `AI_CONTEXT_TOO_LARGE`, etc.).

### 1.2 Provider abstraction and runtime switching

- [x] Create provider interface (for example `AiAssistProvider`) with stable contract for all 3 actions.
- [x] Implement `StubAiAssistProvider` for local/dev test determinism.
- [x] Implement `BedrockAiAssistProvider` for dev runtime.
- [x] Add config switch (`AI_PROVIDER=stub|bedrock`) and fail-fast startup validation for invalid config.
- [x] Standardize structured response schema returned by all providers to prevent provider-specific drift.

### 1.3 Prompt and output governance

- [x] Add prompt templates with explicit versioning (for example `prompt_version=v1` in persisted run records).
- [x] Define strict output schema for:
  - summary text
  - suggested tags list
  - response draft text
- [x] Add output sanitation (strip markdown/control tokens that can break UI rendering).

---

## Phase 2: Data Model and Persistence

### 2.1 Flyway migrations

- [x] Create `ai_assist_runs` table (or equivalent) with at least:
  - `id`
  - `request_id`
  - `action_type` (`SUMMARIZE`, `SUGGEST_TAGS`, `DRAFT_RESPONSE`)
  - `provider` (`stub`, `bedrock`)
  - `model_id`
  - `prompt_version`
  - `input_snapshot` (JSONB)
  - `output_payload` (JSONB)
  - `status` (`SUCCESS`, `FAILED`)
  - `error_code` / `error_message` (nullable)
  - `latency_ms`
  - `created_by`
  - `created_at`
- [x] Add indexes on `request_id`, `action_type`, `created_at`, and `created_by`.
- [x] Add retention policy design (MVP: keep all in dev; production TTL policy tracked and documented).

### 2.2 Request tag persistence (MVP required)

- [x] Implement `tags` and `request_tags` schema per "Tagging Feature Design".
- [x] Implement tag dictionary CRUD subset (list/create/delete) with `TRIAGE`/`ADMIN` RBAC for write operations.
- [x] Implement request tag apply/unapply/list endpoints for all roles with request access.
- [x] Enforce normalized tag uniqueness and duplicate apply idempotency.
- [x] Implement chosen dictionary-delete semantics (`soft-delete` preferred, or `409 in-use` block).

### 2.3 Attachment extraction traceability

- [x] Add optional `ai_assist_context_attachments` table (or JSONB field) storing which attachments were used, parse status, and extraction size.
- [x] Persist parse failures per attachment for debuggability (not only top-level AI failure).

---

## Phase 3: AI Context Builder (Request + Comments + Attachments)

### 3.1 Context assembly

- [x] Build a dedicated context builder service that composes:
  - request title/description
  - comment thread (chronological, size-bounded)
  - attachment-derived context via multimodal input (plus text snippets for text/csv)
- [x] Enforce deterministic truncation policy (for example newest-first or priority-based truncation) and include truncation metadata.
- [x] Define maximum context size guardrails (chars/tokens/attachments) before calling provider.

### 3.2 Attachment ingestion pipeline (Bedrock multimodal-first)

- [x] Parse text-based attachments (`text/plain`, `text/csv`) directly into text snippets.
- [x] For `application/pdf` and image types (`image/jpeg`, `image/png`, `image/webp`), fetch object bytes from S3 and pass them directly to Bedrock multimodal payload (no custom OCR/PDF extraction in MVP).
- [x] Add model capability guardrails (`supportsPdf`, `supportsImage`) and fail fast if configured model cannot process required attachment types.
- [x] Skip unsupported content safely with explicit "not included in AI context" metadata.
- [x] Never expose raw object URLs in AI logs/responses.
- [x] Store per-attachment inclusion status (included/skipped/failed) for auditability.

### 3.3 Performance and reliability controls

- [x] Add per-action timeout and retry strategy (bounded retries, jittered backoff).
- [x] Add circuit-breaker/fallback behavior (for example return graceful failure with retry guidance).
- [x] Add metrics for parse duration, provider latency, and failure rate.

---

## Phase 4: Backend APIs, Authorization, and Bedrock Integration

### 4.1 Service and controller implementation

- [x] Implement service methods for:
  - summarize request
  - suggest tags
  - generate draft response
- [x] Implement controller endpoints under `/api/v1/requests/{id}/ai/*`.
- [x] Return structured payloads with metadata (`provider`, `model`, `latencyMs`, `languageDetected/applied`).

### 4.2 RBAC and ownership checks

- [x] Enforce request-scope access:
  - `USER` can invoke AI only on own requests
  - `TRIAGE`/`ADMIN` can invoke on any request
- [x] Add negative-path tests for unauthorized AI access (`403`).
- [x] Enforce tagging RBAC matrix:
  - `TRIAGE`/`ADMIN` can create/delete dictionary tags
  - request-access users can apply/unapply tags on that request only
  - users without request access cannot read/apply tags for that request

### 4.3 Bedrock runtime integration

- [x] Add Bedrock runtime SDK dependency and configuration.
- [x] Add model selection config (`AI_BEDROCK_MODEL_ID`, optional action-level override) and map model capability flags for attachment handling.
- [x] Add IAM permissions to ECS task role with least privilege:
  - `bedrock:InvokeModel`
  - `bedrock:InvokeModelWithResponseStream` (only if streaming is enabled)
  - resource-scoped to approved model ARNs where possible.
- [x] Ensure no credentials are hard-coded; use IAM role only in dev runtime.

### 4.4 Language-follow behavior

- [x] Implement language inference from user/request context and pass instruction to provider.
- [x] Add explicit fallback rule when language is ambiguous (default English unless clear Chinese signal).
- [x] Verify mixed-language requests still produce coherent output.

---

## Phase 5: Frontend UX (Three Independent Actions)

### 5.1 Request detail AI panel

- [x] Add an `AI Assist` panel on request detail page with 3 buttons:
  - `Summarize`
  - `Suggest Tags`
  - `Draft Response`
- [x] Show independent loading/error states per action (no global blocking spinner).
- [x] Show timestamp/provider metadata for last generated result.

### 5.2 Result handling UX

- [x] Summary action: render concise summary block with copy-to-clipboard.
- [x] Suggest tags action: render chips/list and allow "apply selected tags" flow.
- [x] Add lightweight dictionary management UI for `TRIAGE`/`ADMIN` (create/delete tags).
- [x] Draft response action: provide `Use Draft` action that fills comment textarea only.
- [x] Confirm draft is editable before send and never auto-posts comment.

### 5.3 Failure UX

- [x] Show actionable user errors for timeout/provider failure/context too large.
- [x] Add retry action per AI result card.
- [x] Ensure role-based UI visibility aligns with backend RBAC (no hidden unauthorized calls).

---

## Phase 6: Testing Strategy and CI

### 6.1 Backend tests

- [x] Unit tests for context builder, truncation, prompt rendering, language selection, and output parsing.
- [x] Integration tests for all 3 endpoints with RBAC and persistence verification.
- [x] Provider contract tests:
  - `stub` provider deterministic schema
  - `bedrock` provider adapter mapping and error handling (mocked).
- [x] Attachment ingestion tests including:
  - text/csv snippet extraction path
  - PDF/image multimodal byte path
  - unsupported/failed attachment handling with partial-context result
- [x] Tag domain tests for create/delete/apply/unapply/list behavior, duplicate handling, and delete semantics (`soft-delete` or `409 in-use`).

### 6.2 Frontend tests

- [x] Component tests for AI panel state transitions.
- [x] Playwright E2E journeys for:
  - summarize success
  - suggest-tags + apply-tags success
  - draft generation + "Use Draft" into comment input
  - error/retry flows
  - RBAC negative UI paths
- [x] Keep mocked AI API flows deterministic for CI stability.

### 6.3 CI integration

- [x] Add backend test jobs covering AI modules.
- [x] Add frontend E2E coverage for AI Assist journeys.
- [x] Add post-deploy smoke check in `deploy.yml` for one AI endpoint in dev.

---

## Phase 7: OpenAPI, README, E2E Docs, and Runbooks

- [x] Update `docs/api/openapi.yaml` with all `/ai/*` endpoints and schemas.
- [x] Update README:
  - move AI Assist from "future" to implemented status when done
  - document 3-action UX, tag apply flow, and draft-not-auto-send behavior
  - document provider strategy (`local=stub`, `dev=bedrock`)
- [x] Update `docs/runbooks/deployment.md` with AI env vars and Bedrock verification steps.
- [x] Update deployment runbook with scripted model-access bootstrap command(s) and expected success checks.
- [x] Update `docs/runbooks/incident-response.md` with AI failure triage steps (timeout/provider/model misconfig).
- [x] Add AI-specific journeys into `docs/ai-e2e/user-journeys.md` and include in standard regression batch.

---

## Phase 8: Future Work (Explicitly Not MVP Gate)

- [ ] PII detection/redaction before provider calls.
- [ ] Rate limiting / per-user quota / cost controls for AI actions.
- [ ] Prompt injection hardening and attachment content safety filters.
- [ ] Custom OCR/PDF text extraction fallback for non-multimodal providers (only if future provider strategy requires it).
- [ ] Model quality evaluation harness (golden dataset + regression scoring).
- [ ] Human feedback loop (`thumbs up/down`) with offline prompt iteration pipeline.
- [ ] Multi-provider fallback (Bedrock primary, second provider fallback).

---

## Definition of Done (AI Assist MVP)

- [x] Bedrock account/model access is enabled for the target region and verified from running ECS task.
- [x] Terraform-managed ECS runtime includes AI env configuration and least-privilege Bedrock invoke permissions.
- [x] Bedrock model-access/bootstrap is automated via repo-managed scripts/ad hoc commands (not manual console-only operation).
- [x] All three AI actions work end-to-end from request detail page.
- [x] Tagging feature exists and applied tags persist (backend + frontend + API contract).
- [x] AI context includes request, comments, and parsed attachment content.
- [x] RBAC rules are enforced in backend and reflected in frontend.
- [x] AI outputs are persisted with traceable metadata in database.
- [x] Draft response inserts into comment input and requires explicit user submission.
- [x] OpenAPI, README, runbooks, and E2E docs are synchronized with runtime behavior.
- [x] CI passes with AI backend/frontend coverage and dev smoke verification.
