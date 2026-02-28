# Authentication Guide: JWT (App) and OIDC (CI/CD)

- **Date**: 2026-02-28
- **Status**: Active
- **Scope**: This document explains identity and authentication mechanisms used in this project.

---

## 1. Why this project has two auth mechanisms

This project uses two different identity systems for two different problems:

1. **JWT for application users**
   - Used when human users log in to the web app and call API endpoints.
   - Verified by Spring Boot on every request.
2. **OIDC for CI/CD workload identity**
   - Used when GitHub Actions needs temporary AWS permissions to deploy infrastructure/apps.
   - Verified by AWS IAM via GitHub's OIDC token.

Short version:
- JWT solves **"who is this API caller?"**
- OIDC solves **"which CI job is allowed to touch AWS?"**

---

## 2. JWT in this project (application authentication)

### 2.1 Login flow

1. Frontend submits credentials to `POST /api/v1/auth/login`.
2. `AuthService` authenticates using Spring `AuthenticationManager`.
3. `JwtTokenProvider` signs a JWT and returns it in `AuthResponse`.
4. Frontend stores token in `localStorage`.
5. Frontend adds `Authorization: Bearer <token>` on later requests.

Code references:
- `apps/api/src/main/java/com/suncorp/securehub/controller/AuthController.java`
- `apps/api/src/main/java/com/suncorp/securehub/service/AuthService.java`
- `apps/web/src/auth/AuthContext.tsx`
- `apps/web/src/api/client.ts`

### 2.2 What is inside the JWT

Current token payload is minimal:
- `sub` (username)
- `iat`
- `exp`

Important: roles are **not embedded** in the token in current implementation.
Roles are loaded from DB on each authenticated request.

Code reference:
- `apps/api/src/main/java/com/suncorp/securehub/security/JwtTokenProvider.java`
- `apps/api/src/main/java/com/suncorp/securehub/security/UserDetailsServiceImpl.java`

### 2.3 How server-side validation works

1. `JwtAuthenticationFilter` reads `Authorization` header.
2. If token format is valid and signature/expiry pass, it extracts username.
3. It loads user + authorities from DB via `UserDetailsService`.
4. It puts authenticated principal into Spring `SecurityContext`.
5. Controllers/services use method security (`@PreAuthorize`) and authorities.

Code references:
- `apps/api/src/main/java/com/suncorp/securehub/security/JwtAuthenticationFilter.java`
- `apps/api/src/main/java/com/suncorp/securehub/config/SecurityConfig.java`
- `apps/api/src/main/java/com/suncorp/securehub/controller/AdminUserController.java`

### 2.4 JWT-related runtime config

API config uses:
- `JWT_SECRET` (signing key)
- `JWT_EXPIRATION_MS` (token TTL)
- `CORS_ALLOWED_ORIGINS`

Code reference:
- `apps/api/src/main/resources/application.yml`

In ECS dev runtime today:
- DB credentials: AWS Secrets Manager
- JWT secret: AWS SSM Parameter Store (SecureString), injected into task env

Code reference:
- `infra/terraform/modules/ecs/main.tf`

### 2.5 Security notes for this implementation

- Since token is stored in `localStorage`, protect strongly against XSS.
- Rotating `JWT_SECRET` invalidates all active sessions immediately.
- JWT is stateless for session, but authorization still depends on DB role lookup.
- Default fallback secret values in local configs are for local/dev only and must not be used in real internet-facing environments.

---

## 3. OIDC in this project (GitHub Actions -> AWS)

### 3.1 What problem it solves

Without OIDC, CI/CD usually needs long-lived AWS access keys in GitHub secrets.
With OIDC:
- GitHub issues a short-lived identity token to the workflow.
- AWS IAM verifies that token and returns short-lived AWS credentials.
- No static AWS access key/secret key is required in repo or GitHub.

### 3.2 OIDC flow in this repo

1. Workflow requests `id-token: write`.
2. `aws-actions/configure-aws-credentials` asks GitHub for OIDC token.
3. AWS `sts:AssumeRoleWithWebIdentity` validates token against IAM OIDC provider + trust policy.
4. AWS returns temporary credentials for the IAM role.
5. Workflow uses those temporary credentials for ECR/ECS/Terraform operations.

Code references:
- `.github/workflows/deploy.yml`
- `.github/workflows/terraform.yml`
- `infra/terraform/modules/oidc/main.tf`

### 3.3 IAM trust policy essentials

Trust policy conditions usually check:
- `aud = sts.amazonaws.com`
- `sub` matches expected GitHub repository/ref/environment pattern

In this project, trust policy is configured in Terraform module:
- `aws_iam_openid_connect_provider`
- IAM role with `AssumeRoleWithWebIdentity` trust

Code reference:
- `infra/terraform/modules/oidc/main.tf`

### 3.4 OIDC vs JWT (quick compare)

1. JWT (app):
   - Issuer: your API
   - Audience: your frontend/backend path
   - Purpose: user identity for business endpoints
2. OIDC (CI):
   - Issuer: GitHub Actions
   - Audience: AWS STS
   - Purpose: workload identity for cloud deployment

---

## 4. End-to-end identity picture

### 4.1 User request path

1. Browser logs in -> gets JWT.
2. Browser calls `/api/v1/**` with Bearer token.
3. API verifies token and resolves roles.
4. API enforces RBAC (`USER`, `TRIAGE`, `ADMIN`).

### 4.2 Deployment path

1. GitHub workflow starts.
2. Workflow uses OIDC to obtain temporary AWS credentials.
3. Workflow pushes images / updates ECS / runs Terraform.
4. ECS tasks start with runtime secrets injected from AWS secret stores.

---

## 5. Common misunderstandings

1. **"JWT and OIDC are competing choices."**
   - Not in this architecture. They are complementary and used at different layers.
2. **"JWT token includes roles so DB is not needed."**
   - Not true in current code. Roles are read from DB via `UserDetailsServiceImpl`.
3. **"OIDC means no IAM policy design is needed."**
   - Still need strict least-privilege IAM role permissions and trust conditions.
4. **"Short-lived credentials means no secret risk."**
   - Risk is reduced, not zero. Logs, over-broad trust policy, and over-privileged role are still real risks.

---

## 6. Practical hardening checklist (identity-focused)

- Keep `JWT_SECRET` high entropy and rotate on incident.
- Restrict CORS to exact deployed origin(s).
- Keep OIDC trust conditions scoped as tightly as practical.
- Use separate IAM roles for app deploy and Terraform when possible.
- Keep role policies least-privilege (ECR/ECS/Terraform resources only).
- Avoid printing plan internals/secrets in CI logs.
- Periodically test "negative paths" (invalid JWT, expired JWT, denied OIDC assume-role).

---

## 7. Related docs

- `docs/architecture/ADR-0001.md` (JWT decision record)
- `docs/api/openapi.yaml` (Bearer auth contract)
- `docs/aws-fargate-cicd-checklist.md` (deployment and secret handling checklist)
- `docs/runbooks/deployment.md` (deployment + rotation operations)
- `docs/runbooks/incident-response.md` (authentication incident handling)
