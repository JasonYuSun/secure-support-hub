# AI Security

Last updated: 2026-03-11

## Purpose

This document explains how AI security is handled in the current Secure Support Hub codebase.
It focuses on practical controls, residual risks, and a repeatable red-team loop.

## Red-Team Method (First-Principles)

For this project, the AI security loop is:

1. Identify vulnerabilities (technical, process, and access-control).
2. Score risk (`likelihood x impact`).
3. Implement fix and regression tests.
4. Re-test and record residual risk.

This is executed project-by-project instead of relying only on generic checklists.

## Current AI Security Controls (Implemented)

### 1. Prompt injection hardening (`AI-001`)

- `promptOverride` is sanitized before context building.
- User hint is embedded in a bounded data-only section (`<user_context_hint>`), not as authoritative instructions.
- XML escaping is applied to all user-controlled context fields.

Code references:
- `apps/api/src/main/java/com/suncorp/securehub/service/AiAssistService.java`
- `apps/api/src/main/java/com/suncorp/securehub/service/ai/AiInputSanitizer.java`
- `apps/api/src/main/java/com/suncorp/securehub/service/ai/BedrockAiAssistProvider.java`

### 2. Audit data minimization (`AI-002`)

- `ai_assist_runs.input_snapshot` stores metadata only (no raw request/comment/attachment text).
- `rawHint` is stored as SHA-256 digest for traceability.
- `output_payload` stores metadata only (lengths/counts), not raw generated text.

Code references:
- `apps/api/src/main/java/com/suncorp/securehub/service/AiAssistService.java`

### 3. Attachment context defenses (`AI-003`, `AI-006`, `PLAT-006`)

- Text attachment content is sanitized for prompt-injection phrases and control characters.
- Text context length is capped.
- Binary attachment size is bounded per attachment and per request.
- Declared MIME type is re-verified against file signatures before AI inclusion.

Code references:
- `apps/api/src/main/java/com/suncorp/securehub/service/ai/AiContextBuilder.java`

### 4. Denial-of-wallet protection (`AI-004`)

- Per-user AI endpoint rate limiting is enabled.

Code references:
- `apps/api/src/main/java/com/suncorp/securehub/config/SecurityConfig.java`
- `apps/api/src/main/java/com/suncorp/securehub/filter/RateLimitFilter.java`
- `apps/api/src/main/resources/application.yml`

### 5. Output safety baseline (`AI-008`)

- AI output sanitizer runs at service layer before returning/persisting output.
- Redacts secret-like patterns and private key blocks.
- Removes unsafe control chars and truncates oversized output.

Code references:
- `apps/api/src/main/java/com/suncorp/securehub/service/ai/AiOutputSanitizer.java`
- `apps/api/src/main/java/com/suncorp/securehub/service/AiAssistService.java`

### 6. Governance/Audit improvements (`AI-007`)

- `promptVersion` is configurable (`app.ai.prompt-version`) and persisted to audit rows.

Code references:
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/main/java/com/suncorp/securehub/service/AiAssistService.java`

## Platform Security Controls (Implemented)

### Access and endpoint hardening

- AI controller has explicit method-level RBAC annotation.
- Actuator is hardened: only health is public; other actuator endpoints require `ADMIN`.
- API docs are controllable by `app.docs.public`; production profile sets it to `false`.
- JWT secret has no insecure fallback and is validated at startup.
- Request list sorting uses allowlist validation to avoid unhandled server errors.

Code references:
- `apps/api/src/main/java/com/suncorp/securehub/controller/AiAssistController.java`
- `apps/api/src/main/java/com/suncorp/securehub/config/SecurityConfig.java`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/main/java/com/suncorp/securehub/security/JwtTokenProvider.java`
- `apps/api/src/main/java/com/suncorp/securehub/controller/SupportRequestController.java`

### CI/CD security checks

Backend CI includes:
- Gitleaks (secret scanning)
- Semgrep (SAST)
- Trivy (container image vulnerability scan)

Code references:
- `.github/workflows/backend.yml`

## Residual Risk (Accepted for Side-Project Scope)

The following are currently accepted (documented tradeoffs for simpler infrastructure):

- `AI-005`: Bedrock IAM policy is broader than strict single-model least privilege.
- `PLAT-008`: ECS tasks run in public subnets with public IPs.
- `PLAT-009`: CloudWatch log retention remains 14 days.
- `PLAT-010`: No WAF attached to ALB.

These are explicit scope decisions for now and can be revisited for production-hardening.

## Validation and Regression

Security fixes are backed by targeted tests in `apps/api/src/test`, including:

- Prompt injection integration tests
- Rate-limit integration tests
- Actuator/docs access tests
- Audit snapshot minimization tests
- MIME/signature and context budget tests
- Controller method-security tests
- JWT secret validation tests

## Related Docs

- `docs/ai-red-team/ai-redteam-report.md`
- `docs/prompts/ai-red-team-standard-prompt.md`
- `docs/prompts/ai-red-team-customized-prompt.md`
- `docs/runbooks/deployment.md`
