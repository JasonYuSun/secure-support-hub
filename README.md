# Secure Support Hub (Side Project)

A production-style, secure support request management app built to demonstrate **end-to-end ownership** across **React (TypeScript)**, **Java / Spring Boot**, **REST APIs**, **CI/CD**, and **AWS ECS Fargate**.

---

## Table of Contents

- [Secure Support Hub (Side Project)](#secure-support-hub-side-project)
  - [Table of Contents](#table-of-contents)
  - [Why this project](#why-this-project)
  - [Key capabilities](#key-capabilities)
    - [Project idea: Secure Support Hub](#project-idea-secure-support-hub)
    - [Core user scenario](#core-user-scenario)
    - [Product features](#product-features)
    - [Engineering \& operations features](#engineering--operations-features)
  - [Architecture](#architecture)
    - [Components](#components)
  - [Tech stack](#tech-stack)
    - [Frontend](#frontend)
    - [Backend](#backend)
    - [Infra \& Ops](#infra--ops)
  - [Local development](#local-development)
    - [Prerequisites](#prerequisites)
    - [Quick start (recommended)](#quick-start-recommended)
    - [Verification (one command)](#verification-one-command)
  - [API overview](#api-overview)
  - [Security model](#security-model)
    - [Authentication](#authentication)
    - [Authorization (RBAC)](#authorization-rbac)
    - [Secure-by-default practices](#secure-by-default-practices)
  - [Testing strategy](#testing-strategy)
    - [Backend](#backend-1)
    - [Frontend](#frontend-1)
  - [CI/CD](#cicd)
  - [Deployment](#deployment)
    - [Docker (dev)](#docker-dev)
    - [AWS ECS Fargate (dev first)](#aws-ecs-fargate-dev-first)
    - [AWS services used](#aws-services-used)
  - [Project structure](#project-structure)
  - [Roadmap](#roadmap)
    - [MVP](#mvp)
    - [Phase 2](#phase-2)
    - [Phase 3](#phase-3)
  - [License](#license)

---

## Why this project

This repo is designed to match a Senior Software Engineer role that values:

- **End-to-end delivery**: from solution design → implementation → production readiness → support
- **Maintainability**: clear boundaries, strong contracts, automated tests, sensible defaults
- **Operational discipline**: metrics, logs, health checks, runbooks, and incident readiness
- **Platform fit**: containerized services deployed on **AWS ECS Fargate** (MVP target)

It intentionally prioritizes **simplicity and extensibility** over novelty.

---

## Key capabilities

### Project idea: Secure Support Hub
A production-style web app that lets teams create, triage, and track support requests with:
- a clean React (TypeScript) UI
- Spring Boot backend services (REST APIs)
- AuthN/AuthZ (JWT + RBAC)
- strong observability (logs/metrics/traces)
- CI/CD + automated tests
- AWS deployment on ECS Fargate (dev first, `ap-southeast-2`)
- “AI-assisted” features that are safe & practical (summaries, suggested tags, draft responses)

### Core user scenario

1. A user logs in (JWT).
2. Creates a support request with title/description + attachments.
3. “AI Assist” button:
	• summarizes the issue
	• suggests tags (e.g., billing, login, network)
	• proposes a first response draft
4. A triage engineer views the queue

### Product features

- **Authentication + Authorization**
  - JWT-based auth
  - Role-based access control (RBAC): `USER`, `TRIAGE`, `ADMIN`
  - Admin user/role management APIs (`/api/v1/admin/users`, `/api/v1/admin/roles`)
  - Admin UI for user/role updates at `/admin/users`
  - Guardrails: admins cannot remove their own `ADMIN` role; concurrent updates return conflict (`409`)
- **Support Requests**
  - Create, view, search, filter, paginate
  - Status workflow: `OPEN → IN_PROGRESS → RESOLVED → CLOSED`
  - Comments and assignment
- **AI Assist (opt-in, human-in-the-loop)**
  - Summarize issue description
  - Suggest tags
  - Draft a response (never auto-sent, always reviewable)

### Engineering & operations features

- Versioned REST APIs + OpenAPI/Swagger documentation
- Database migrations with Flyway
- Structured logging + request correlation IDs
- Health/readiness endpoints + metrics
- CI checks: lint, unit tests, integration tests, build/publish images
- AWS-ready container deployment on ECS Fargate with environment-first rollout (`dev` before `prod`)

---

## Architecture

At a high level:

- **Web** (React + TypeScript) calls
- **API** (Spring Boot) which persists to
- **Postgres** and
- **LLM provider** via a dedicated AI endpoint for assistive features.

### Components

- `apps/web`: UI for request creation, triage queue, and detail views
- `apps/api`: REST APIs, auth, validation, business rules, persistence
- `infra/`: Docker Compose for local dev and Terraform/AWS deployment assets

Detailed runtime architecture documentation:

- `docs/architecture/aws-fargate-runtime-architecture.md`
- `docs/architecture/authentication-jwt-oidc-guide.md`

---

## Tech stack

### Frontend

- React + TypeScript (Vite)
- TanStack Query (server state)
- Playwright (E2E tests)

### Backend

- Java 21 (or 17) + Spring Boot 3
- Spring Security + JWT
- Spring Data JPA + Hibernate
- Flyway (schema migrations)
- Testcontainers (integration tests)
- springdoc-openapi (OpenAPI generation)

### Infra & Ops

- Docker + Docker Compose (local)
- AWS ECS Fargate (dev-first deployment target)
- GitHub Actions CI/CD
- AWS: ECR/ECS Fargate/RDS/ALB/CloudWatch/S3

---

## Local development

### Prerequisites

- Docker + Docker Compose
- Java 21 (or 17)
- Node.js 20+

### Quick start (recommended)

1. Copy environment templates:

    ```bash
    cp apps/api/.env.example apps/api/.env
    cp apps/web/.env.example apps/web/.env
    ```

2. Start the full stack:

    ```bash
    docker compose -f infra/docker-compose/docker-compose.yml up --build
    ```

3. Run backend locally:

    ```bash
    cd apps/api
    ./gradlew bootRun
    ```

4. Run frontend locally:

    ```bash
    cd apps/web
    npm install
    npm run dev
    ```

### Verification (one command)

Run all non-GUI local checks end-to-end:

- Start full stack with Docker Compose
- Check backend health endpoint
- Run backend tests (`./gradlew test`)
- Run frontend clean install + production build (`npm ci && npm run build`)
- Check Swagger and frontend URLs via `curl`

```bash
make verify
```

Alternative:

```bash
./scripts/verify.sh
```

By default, the script stops the Docker stack when finished.  
Use `KEEP_STACK_UP=1 make verify` if you want containers to remain running.

**Default local URLs:**

| Service            | URL                                         |
| ------------------ | ------------------------------------------- |
| Web                | <http://localhost:5173>                     |
| API                | <http://localhost:8080>                     |
| OpenAPI/Swagger UI | <http://localhost:8080/swagger-ui.html>     |
| Health             | <http://localhost:8080/actuator/health>     |
| Metrics            | <http://localhost:8080/actuator/prometheus> |

> **Note:** Exact ports/paths may differ based on configuration; see the `.env.example` files.

---

## API overview

The API follows consistent patterns:

- Versioned base path: `/api/v1`
- Pagination: `page`, `size`, `sort`
- Consistent error format: `code`, `message`, `details`, `requestId`
- Validation errors return field-level details

**Main resource groups (high level):**

| Method  | Path                             | Description                    |
| ------- | -------------------------------- | ------------------------------ |
| `POST`  | `/api/v1/auth/login`             | Authenticate and receive JWT   |
| `GET`   | `/api/v1/me`                     | Current user profile + roles   |
| `GET`   | `/api/v1/admin/users`            | List users (ADMIN only)        |
| `GET`   | `/api/v1/admin/users/{id}`       | Get user details (ADMIN only)  |
| `PATCH` | `/api/v1/admin/users/{id}/roles` | Replace user roles (ADMIN only)|
| `GET`   | `/api/v1/admin/roles`            | List available roles (ADMIN only) |
| `GET`   | `/api/v1/requests`               | List requests with filters     |
| `POST`  | `/api/v1/requests`               | Create request                 |
| `GET`   | `/api/v1/requests/{id}`          | Request details                |
| `POST`  | `/api/v1/requests/{id}/comments` | Add comment                    |
| `PATCH` | `/api/v1/requests/{id}`          | Status/assignee updates (RBAC) |
| `POST`  | `/api/v1/ai/summarize`           | AI summary (opt-in)            |
| `POST`  | `/api/v1/ai/suggest-tags`        | Tag suggestions (opt-in)       |
| `POST`  | `/api/v1/ai/draft-response`      | Response draft (opt-in)        |

The canonical contract is published via OpenAPI:

- Swagger UI (local): `/swagger-ui.html`
- Source: `docs/api/openapi.yaml` (or generated)

---

## Security model

### Authentication

- JWT access tokens issued by the API
- Tokens include user identity + roles
- All non-public endpoints require `Authorization: Bearer <token>`

### Authorization (RBAC)

| Role     | Permissions                                                   |
| -------- | ------------------------------------------------------------- |
| `USER`   | Create requests, view own requests, comment on own requests   |
| `TRIAGE` | View and manage all requests, assign requests, change status  |
| `ADMIN`  | User/role management, system-level configuration              |

### Secure-by-default practices

- Input validation on all writes
- Centralized exception handling (no stack traces leaked)
- Rate limiting on auth endpoints
- Audit logging for sensitive state changes
- Secrets are never committed; use env vars / AWS Secrets Manager / SSM Parameter Store

---

## Testing strategy

This project aims for practical, production-like coverage.

### Backend

- Unit tests: service/business rules, mappers, validators
- Integration tests (Testcontainers): DB migrations, repositories, controllers
- Contract tests: validate API expectations across versions

```bash
cd apps/api
./gradlew test
```

### Frontend

- Component tests
- E2E tests with Playwright

```bash
cd apps/web
npm run test:e2e
```

---

## CI/CD

GitHub Actions workflows (in `.github/workflows/`) typically include:

- **Terraform (IaC):** `fmt/validate/plan` on infra PRs, controlled `apply` on `main` (OIDC, no static AWS keys)
- **Frontend:** install → lint → test → build
- **Backend:** build → test → security scan → Docker build
- **Image publishing:** push to registry (e.g., GHCR/ECR) on main branch
- **Deployment:** build/push images and update ECS task definitions/services (dev first)

Sensitive data handling baseline:

- Runtime secrets (DB/JWT/API keys) live in AWS Secrets Manager / SSM Parameter Store, not in GitHub workflow YAML
- GitHub Environment Variables are non-sensitive metadata only
- Pipeline logs/artifacts should publish sanitized outputs only (no plaintext secret values)

---

## Deployment

### Docker (dev)

- Fastest route for local demo and iteration
- Use Compose files under `infra/docker-compose/`

### AWS ECS Fargate (dev first)

Current cloud deployment path:

- Runtime: ECS Fargate
- Region: `ap-southeast-2`
- Environment rollout: `dev` first, then `prod`
- Components: `web` (Nginx static hosting), `api` (Spring Boot), `rds` (PostgreSQL), `alb`, `ecr`

Execution checklist:

- `docs/aws-fargate-cicd-checklist.md`
  - `Phase 6`: runtime configuration mapping (secret vs non-secret split)
  - `Phase 7`: GitHub OIDC + app CD + Terraform pipeline (IaC)

### AWS services used

Terraform provisions:

- ECS cluster and services (Fargate launch type)
- Application Load Balancer (ALB)
- ECR repositories
- RDS Postgres
- S3 for attachments (Phase 3)
- IAM roles and OIDC trust for CI/CD

---

## Project structure

```
secure-support-hub/
  apps/
    web/                     # React + TypeScript
    api/                     # Spring Boot REST API
  infra/
    docker-compose/
    terraform/               # AWS provisioning
  docs/
    architecture/
      ADR-0001.md
      aws-fargate-runtime-architecture.md
    runbooks/
      incident-response.md
      deployment.md
    aws-fargate-cicd-checklist.md
    api/
      openapi.yaml
  .github/workflows/
  README.md
```

---

## Roadmap

### MVP

- Auth (JWT) + RBAC
- Support request CRUD + comments + workflow
- OpenAPI docs
- Unit + integration tests
- Compose + AWS ECS Fargate deployment (`dev` first)

### Phase 2

- Attachments backed by S3
- AI Assist feature

### Phase 3

- Canary/blue-green deployment workflow
- Multi-tenant support (org scoping)
- Full observability stack (OTel collector + dashboards)

---

## License

This project is provided under the [MIT License](LICENSE).
