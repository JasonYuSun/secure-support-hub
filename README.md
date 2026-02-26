# Secure Support Hub (Side Project)

A production-style, secure support request management app built to demonstrate **end-to-end ownership** across **React (TypeScript)**, **Java / Spring Boot**, **REST APIs**, **CI/CD**, and **AWS + Kubernetes (OpenShift-friendly)**.

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
    - [Kubernetes (dev/prod)](#kubernetes-devprod)
    - [AWS](#aws)
  - [Observability \& operations](#observability--operations)
    - [Health checks](#health-checks)
    - [Logging](#logging)
    - [Metrics \& tracing](#metrics--tracing)
    - [Runbooks](#runbooks)
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
- **Platform fit**: containerized services deployed on **Kubernetes**, compatible with **OpenShift**, and deployable to **AWS**

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
- Kubernetes deployment (with OpenShift-friendly manifests)
- “AI-assisted” features that are safe & practical (summaries, suggested tags, draft responses)

### Core user scenario

1. A user logs in (JWT).
2. Creates a support request with title/description + attachments.
3. “AI Assist” button:
	• summarizes the issue
	• suggests tags (e.g., billing, login, kubernetes)
	• proposes a first response draft
4. A triage engineer views the queue, filters by SLA priority, assigns owners.
5. Status changes trigger events (audit log + notifications).
6. Ops view shows request volume, latency, error rate, SLA breaches.

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
- Kubernetes manifests with an OpenShift overlay

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
- `infra/`: Docker Compose for local dev, Kubernetes manifests, Terraform

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
- Kubernetes (base manifests + overlays)
- OpenTelemetry instrumentation (tracing)
- Prometheus metrics endpoint
- GitHub Actions CI/CD
- AWS: ECR/EKS/RDS/S3

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
- Secrets are never committed; use env vars / Kubernetes Secrets

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

- **Frontend:** install → lint → test → build
- **Backend:** build → test → security scan → Docker build
- **Image publishing:** push to registry (e.g., GHCR/ECR) on main branch
- **Deployment:** apply manifests / Helm (environment-dependent)

**Recommended add-ons:**

- Dependabot for dependency updates
- CodeQL for static analysis
- Trivy/Grype for container scanning

---

## Deployment

### Docker (dev)

- Fastest route for local demo and iteration
- Use Compose files under `infra/docker-compose/`

### Kubernetes (dev/prod)

Kubernetes manifests live in:

- `infra/k8s/base`: standard k8s resources
- `infra/k8s/overlays/dev`: dev values (lower resources, local ingress)
- `infra/k8s/overlays/prod`: production-like values

Typical commands (kustomize):

```bash
kubectl apply -k infra/k8s/overlays/dev
```

### AWS

Terraform provisions:

- EKS cluster
- ECR repositories
- RDS Postgres
- S3 for attachments (Phase 3)
- IAM roles for service accounts (IRSA)

---

## Observability & operations

### Health checks

- Liveness/readiness via Spring Boot Actuator

### Logging

- Structured JSON logs
- Request IDs for traceability
- Audit logs for key state changes (status/assignee/role changes)

### Metrics & tracing

- Prometheus scraping endpoint
- OpenTelemetry traces exported to a collector
- Dashboards stored under `docs/observability/`

### Runbooks

Operational documentation is stored in:

- `docs/runbooks/incident-response.md`
- `docs/runbooks/deployment.md`

---

## Project structure

```
secure-support-hub/
  apps/
    web/                     # React + TypeScript
    api/                     # Spring Boot REST API
  infra/
    docker-compose/
    k8s/
      base/
      overlays/
        dev/
        prod/
        openshift/
    terraform/               # AWS provisioning
  docs/
    architecture/
      ADR-0001.md
    runbooks/
      incident-response.md
      deployment.md
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
- Compose + Kubernetes deployment

### Phase 2

- SLA priority & breach detection
- Audit log expansion
- Notifications (webhook/email stub)
- Security scanning in CI

### Phase 3

- Attachments backed by S3
- Canary/blue-green deployment workflow
- Multi-tenant support (org scoping)
- Full observability stack (OTel collector + dashboards)

---

## License

This project is provided under the [MIT License](LICENSE).
