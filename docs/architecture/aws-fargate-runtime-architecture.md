# AWS Fargate Runtime Architecture

- **Date**: 2026-02-27
- **Status**: Active (MVP target runtime)
- **Scope**: Runtime architecture for the public web app and API on AWS

---

## 1. Purpose

This document explains the deployed runtime architecture for Secure Support Hub on AWS, including:

- core components
- how requests flow from browser to backend
- how authentication and secrets work
- how CI/CD updates running services

---

## 2. High-level components

The MVP runtime uses:

- **Amazon ALB**: public internet entry point
- **ECS Fargate service (web)**: Nginx container serving React static assets
- **ECS Fargate service (api)**: Spring Boot REST API
- **Amazon RDS PostgreSQL**: relational database
- **Amazon ECR**: container image registry
- **AWS Secrets Manager / SSM Parameter Store**: runtime secret storage (`DB_PASSWORD`, `JWT_SECRET`, API keys)
- **CloudWatch Logs/Metrics**: runtime observability

Region default for this project: `ap-southeast-2`.

---

## 3. Request path and routing

### 3.1 Browser to frontend

1. User opens the public URL (ALB DNS or custom domain).
2. ALB forwards non-`/api/*` traffic to the `web` target group.
3. Nginx serves static files from `/usr/share/nginx/html`.
4. For SPA routes, Nginx fallback returns `index.html`.

### 3.2 Browser to backend API

1. Frontend uses `VITE_API_URL` (current default: `/api/v1`).
2. Browser sends API requests to the same origin path.
3. ALB listener rule routes `/api/*` to the `api` target group.
4. Spring Boot handles `/api/v1/**` endpoints and talks to RDS.

---

## 4. Authentication and authorization flow

1. Frontend calls `POST /api/v1/auth/login`.
2. API validates credentials and returns JWT + user summary.
3. Frontend stores token and sends `Authorization: Bearer <token>` on API calls.
4. API validates JWT in `JwtAuthenticationFilter`.
5. Spring Security enforces role-based access (`USER`, `TRIAGE`, `ADMIN`) at endpoint/method level.

---

## 5. Secret handling model

Sensitive values are **not** committed to repo and **not** hardcoded in pipeline YAML.

- Runtime app secrets live in **AWS Secrets Manager / SSM Parameter Store**.
- ECS task definitions reference secret ARNs in the `secrets` field.
- ECS injects values at runtime into containers.
- API reads injected env vars (for example DB/JWT settings).

This applies especially for:

- `DB_PASSWORD`
- `JWT_SECRET`
- third-party API keys

---

## 6. Deployment and update flow

1. CI builds and tests app code.
2. CD builds container images and pushes tags to ECR.
3. CD registers new ECS task definition revisions (image updates + secret ARN references).
4. ECS services roll out new tasks.
5. ALB routes traffic to healthy tasks.

Terraform pipeline (recommended) manages infrastructure lifecycle and state separately from app deployment workflow.

---

## 7. Network and exposure boundaries

- **Public exposure**: ALB only
- **Direct container exposure**: none (tasks are behind ALB target groups)
- **DB exposure**: private (RDS only reachable from API security group)
- **Inter-service traffic**: web and api communicate through ALB routing model (same public origin from browser perspective)

---

## 8. Operational checks

Minimum post-deploy checks:

- ALB endpoint reachable
- `/actuator/health` returns `UP`
- login flow works
- one end-to-end request lifecycle works (create -> view -> update/comment)

---

## 9. Related documents

- `docs/aws-fargate-cicd-checklist.md`
- `docs/runbooks/deployment.md`
- `docs/runbooks/incident-response.md`
- `docs/architecture/ADR-0001.md`
