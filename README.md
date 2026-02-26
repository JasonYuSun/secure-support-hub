# Secure Support Hub (Side Project)

A production-style, secure support request management app built to demonstrate **end-to-end ownership** across **React (TypeScript)**, **Java / Spring Boot**, **REST APIs**, **CI/CD**, and **AWS + Kubernetes (OpenShift-friendly)** — with **AI-assisted development** transparently showcased via curated prompts in this repo.

---

## Table of Contents

- [Secure Support Hub (Side Project)](#secure-support-hub-side-project)
  - [Table of Contents](#table-of-contents)
  - [Why this project](#why-this-project)
  - [Key capabilities](#key-capabilities)
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
    - [Optional AWS](#optional-aws)
  - [Observability \& operations](#observability--operations)
    - [Health checks](#health-checks)
    - [Logging](#logging)
    - [Metrics \& tracing](#metrics--tracing)
    - [Runbooks](#runbooks)
  - [AI-assisted development (vibe coding)](#ai-assisted-development-vibe-coding)
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

### Product features

- **Authentication + Authorization**
  - JWT-based auth
  - Role-based access control (RBAC): `USER`, `TRIAGE`, `ADMIN`
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
- **Postgres** and optionally uses
- **Redis** (rate limiting / caching) and
- **LLM provider** via a dedicated AI endpoint for assistive features.

### Components

- `apps/web`: UI for request creation, triage queue, and detail views
- `apps/api`: REST APIs, auth, validation, business rules, persistence
- `infra/`: Docker Compose for local dev, Kubernetes manifests, optional Terraform

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
- Optional AWS: ECR/EKS/RDS/S3

---

## Local development

### Prerequisites

- Docker + Docker Compose
- Java 21 (or 17)
- Node.js 20+
- (Optional) Kubernetes: kind/minikube + kubectl

### Quick start (recommended)

1. Copy environment templates:

    ```bash
    cp apps/api/.env.example apps/api/.env
    cp apps/web/.env.example apps/web/.env
    ```

2. Start dependencies (and optionally the full stack):

    ```bash
    # Option A: start the full stack via compose
    docker compose -f infra/docker-compose/docker-compose.yml up --build

    # Option B: start only dependencies (postgres, redis), run apps locally
    docker compose -f infra/docker-compose/docker-compose.deps.yml up -d
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
| `ADMIN`  | User/role management (if enabled), system-level configuration |

### Secure-by-default practices

- Input validation on all writes
- Centralized exception handling (no stack traces leaked)
- Rate limiting (optional) on auth endpoints
- Audit logging for sensitive state changes
- Secrets are never committed; use env vars / Kubernetes Secrets

---

## Testing strategy

This project aims for practical, production-like coverage.

### Backend

- Unit tests: service/business rules, mappers, validators
- Integration tests (Testcontainers): DB migrations, repositories, controllers
- Contract tests (optional): validate API expectations across versions

```bash
cd apps/api
./gradlew test
```

### Frontend

- Component tests (optional)
- E2E tests with Playwright

```bash
cd apps/web
npm run test:e2e
```

---

## CI/CD

GitHub Actions workflows (in `.github/workflows/`) typically include:

- **Frontend:** install → lint → test → build
- **Backend:** build → test → security scan (optional) → Docker build
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
- `infra/k8s/overlays/openshift`: OpenShift-specific objects/tweaks

Typical commands (kustomize):

```bash
kubectl apply -k infra/k8s/overlays/dev
# or
kubectl apply -k infra/k8s/overlays/openshift
```

### Optional AWS

Terraform (optional) provisions:

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
- OpenTelemetry traces exported to a collector (optional)
- Dashboards (optional) stored under `docs/observability/`

### Runbooks

Operational documentation is stored in:

- `docs/runbooks/incident-response.md`
- `docs/runbooks/deployment.md`

---

## AI-assisted development (vibe coding)

This repo explicitly documents how AI tools were used to accelerate development while maintaining quality.

- Prompt history lives in: `prompts/`
- Each prompt file includes:
  - Goal and scope
  - Constraints (security, performance, maintainability)
  - Prompt text (what was asked)
  - Output checklist (what must be produced)
  - Verification steps (tests, reviews, manual checks)

**Guardrails:**

- AI suggestions are never auto-applied to production state
- AI outputs are treated as drafts and must pass:
  - Tests
  - Code review checklist
  - Security constraints (no secrets, no risky patterns)

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
    terraform/               # optional AWS provisioning
  docs/
    architecture/
      ADR-0001.md
      threat-model.md
    runbooks/
      incident-response.md
      deployment.md
    api/
      openapi.yaml
  prompts/                   # AI-assisted dev prompts (vibe coding)
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

This project is provided under the [MIT License](LICENSE) (or replace with your preferred license).
