# Deployment Runbook

**Service**: Secure Support Hub  
**Owner**: Engineering Team  
**Last Updated**: 2026-02-27

---

## Prerequisites

- Docker ≥ 24 and Docker Compose plugin installed
- Access to the container registry (for prod)
- `aws` CLI configured for `ap-southeast-2` (AWS cloud deployments)

---

## 1. Local Development (Docker Compose)

### First-time setup

```bash
# 1. Clone and enter the repo
cd secure-support-hub

# 2. Copy env files
cp apps/api/.env.example apps/api/.env
cp apps/web/.env.example apps/web/.env

# 3. Edit .env files with real values (JWT_SECRET at minimum)
# apps/api/.env: Set JWT_SECRET to a random 64-byte base64 string
openssl rand -base64 64

# 4. Build and start the full stack
docker compose -f infra/docker-compose/docker-compose.yml up --build

# 5. Verify
curl http://localhost:8080/actuator/health
# Open http://localhost:5173
```

### Day-to-day

```bash
# Start (no rebuild)
docker compose -f infra/docker-compose/docker-compose.yml up -d

# Stop
docker compose -f infra/docker-compose/docker-compose.yml down

# Rebuild after code changes
docker compose -f infra/docker-compose/docker-compose.yml up --build -d

# View logs
docker compose -f infra/docker-compose/docker-compose.yml logs -f api
```

---

## 2. AWS ECS Fargate — Dev First (Primary Cloud Path)

- Target region: `ap-southeast-2`
- Rollout order: `dev` first, then `prod`
- Canonical step-by-step checklist:
  - `docs/aws-fargate-cicd-checklist.md`

Use the checklist phases in order:

1. AWS account bootstrap + SSO CLI profile
2. Terraform bootstrap and infrastructure provisioning
3. GitHub OIDC + CI/CD deployment pipeline
4. Post-deploy verification and rollback drill

---

## 3. Rollback Procedure

### AWS ECS Fargate (Primary Cloud Path)

```bash
# 1) Identify previous stable task definition revision
aws ecs describe-services \
  --cluster <ecs-cluster-name> \
  --services <ecs-api-service-name> \
  --region ap-southeast-2

# 2) Update service to the previous known-good task definition
aws ecs update-service \
  --cluster <ecs-cluster-name> \
  --service <ecs-api-service-name> \
  --task-definition <previous-task-def-arn> \
  --region ap-southeast-2
```

### Docker Compose

```bash
# Pull and start the previous image tag
# Edit docker-compose.yml to point to previous image, then:
docker compose -f infra/docker-compose/docker-compose.yml up -d
```

---

## 4. Database Migrations

Flyway migrations run automatically on API startup. To run manually:

```bash
cd apps/api
./gradlew flywayMigrate \
  -Pflyway.url=jdbc:postgresql://localhost:5432/securehub \
  -Pflyway.user=securehub \
  -Pflyway.password=changeme
```

To view migration status:
```bash
./gradlew flywayInfo
```

> [!CAUTION]
> Never run `flywayClean` in production — it drops all objects in the schema.

---

## 5. Secret Rotation (JWT_SECRET)

Rotating the JWT secret invalidates all active user sessions.

1. Generate a new secret: `openssl rand -base64 64`
2. Update `JWT_SECRET` in the deployment environment (AWS Secrets Manager / SSM Parameter Store, or local `.env`)
3. Restart the API ECS service or local container
4. Inform users: all active sessions will be terminated; users must log in again
