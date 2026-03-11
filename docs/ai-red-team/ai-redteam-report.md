# AI Red-Team Report — Secure Support Hub (Current State)

**Version:** 2.0  
**Date:** 2026-03-11  
**Scope:** Current `main` branch implementation  
**Report path:** `docs/ai-red-team/ai-redteam-report.md`

---

## 1. Executive Summary

This report is the **current-state update** of the red-team work.  
The original baseline report (v1, 2026-03-05) identified 18 issues. Most application and CI/CD issues have been remediated with code + tests.

### Current status summary

- **Remediated:** 14 issues
- **Open (accepted for side-project infra scope):** 4 issues

Open accepted issues are infra-focused:
- `AI-005` (Bedrock IAM scope)
- `PLAT-008` (ECS public subnets/public IP)
- `PLAT-009` (CloudWatch retention 14 days)
- `PLAT-010` (no WAF on ALB)

---

## 2. Method and Evidence

This update maps each issue to current code evidence and remediation status.

Primary evidence sources:
- Backend app code under `apps/api/src/main/java`
- Backend tests under `apps/api/src/test/java`
- CI config: `.github/workflows/backend.yml`
- Infra config: `infra/terraform/modules/**`, `infra/terraform/envs/dev/**`

---

## 3. Vulnerability Status Register

| ID | Title | Current Status | Notes |
|---|---|---|---|
| AI-001 | `promptOverride` prompt injection | ✅ Fixed | Input sanitizer + bounded data-only hint section + XML escaping controls in provider/context path. |
| AI-002 | PII in `ai_assist_runs` snapshots | ✅ Fixed | Audit snapshots are metadata-only; raw hint stored as SHA-256 digest. |
| AI-003 | Indirect prompt injection via attachments | ✅ Fixed | Text attachment sanitization + injection phrase filtering + MIME signature checks. |
| AI-004 | No rate limiting on AI endpoints | ✅ Fixed | Per-user AI rate limiting filter enabled in security chain. |
| AI-005 | Bedrock IAM policy too broad | ⚠️ Accepted Risk (Open) | Kept broader IAM scope intentionally for side-project simplicity. |
| AI-006 | Unbounded attachment context size | ✅ Fixed | Per-file and per-request binary budgets + text caps. |
| AI-007 | Hardcoded `promptVersion` | ✅ Fixed | `app.ai.prompt-version` config added and persisted to audit rows. |
| AI-008 | No output safety layer (stub/local) | ✅ Fixed | Service-level `AiOutputSanitizer` redacts secret-like output patterns. |
| PLAT-001 | Public actuator + full details | ✅ Fixed | Only health is public; other actuator endpoints require `ADMIN`; details are authorized-only. |
| PLAT-002 | Hardcoded fallback JWT secret | ✅ Fixed | No default secret; startup validation enforces non-empty and length >= 32. |
| PLAT-003 | Public Swagger/OpenAPI in prod | ✅ Fixed | `app.docs.public` gate; prod profile sets docs to non-public by default. |
| PLAT-004 | AI controller lacks method-level RBAC | ✅ Fixed | `@PreAuthorize("hasAnyRole('USER','TRIAGE','ADMIN')")` added. |
| PLAT-005 | Missing security scans in CI | ✅ Fixed | Backend CI now includes Gitleaks, Semgrep, and Trivy image scanning. |
| PLAT-006 | MIME not re-verified at AI ingestion | ✅ Fixed | Byte signature checks enforce declared MIME consistency before AI inclusion. |
| PLAT-007 | Unvalidated `sort` causes 500 | ✅ Fixed | Sort field allowlist validation returns `400` for invalid fields. |
| PLAT-008 | ECS in public subnets/public IP | ⚠️ Accepted Risk (Open) | Reverted hardening; kept current simple infra model by design. |
| PLAT-009 | CloudWatch retention only 14 days | ⚠️ Accepted Risk (Open) | Reverted retention hardening; 14-day retention retained for side project. |
| PLAT-010 | No WAF in front of ALB | ⚠️ Accepted Risk (Open) | WAF hardening was reverted intentionally for scope simplicity. |

---

## 4. Current Security Posture (Implemented Controls)

### AI pipeline controls

- Prompt/input sanitization and strict instruction/data separation.
- XML escaping of user-controlled context fields.
- Attachment sanitization + MIME signature re-verification.
- Context-size controls for text and binary content.
- Per-user AI rate limiting.
- Output sanitization before returning/persisting AI output.
- Metadata-only audit records with configurable `promptVersion`.

### Platform controls

- Method-level RBAC guard on AI controller.
- JWT secret mandatory and validated at startup.
- Actuator hardened (health public only; others admin).
- API docs controllable via config; production defaults to non-public.
- Backend CI security checks (secret scan, SAST, image scan).
- Request list sort allowlist validation.

---

## 5. Open Risks (Accepted for Side-Project Scope)

These are explicitly accepted tradeoffs and not implementation gaps by accident:

1. **AI-005 (Bedrock IAM scope):** Broader policy reduces friction for model/profile changes during demos.
2. **PLAT-008 (Network exposure):** Public-subnet ECS keeps infra simpler/cost-lower for MVP.
3. **PLAT-009 (14-day logs):** Short retention accepted for non-production side-project usage.
4. **PLAT-010 (No WAF):** Deferred to production-hardening stage.

If this project moves to production-like exposure, these four should be prioritized next.

---

## 6. Validation Snapshot

Representative tests/workflow checks covering remediations:

- `AiPromptInjectionIT`
- `AiRateLimitIT`
- `AiAuditSnapshotIT`
- `AiPromptVersionIT`
- `AiContextBuilderTest`
- `AiOutputSanitizerTest`
- `AiAssistControllerMethodSecurityIT`
- `ActuatorSecurityIT`
- `SwaggerSecurityIT`
- `SupportRequestControllerIT` (invalid sort input case)
- `.github/workflows/backend.yml` security jobs: Gitleaks, Semgrep, Trivy

---

## 7. Notes

- This v2 report supersedes the historical assumptions in v1 and reflects the **current codebase state**.
- Infra-related accepted risks are documented intentionally to match side-project scope decisions.
