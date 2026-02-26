# Deployment Runbook

**Service**: Secure Support Hub  
**Owner**: Engineering Team  
**Last Updated**: 2026-02-26

---

## Prerequisites

- Docker ≥ 24 and Docker Compose plugin installed
- kubectl configured (for Kubernetes deployments)
- kustomize ≥ 5 (bundled with kubectl ≥ 1.27)
- Access to the container registry (for prod)

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

## 2. Kubernetes — Dev Overlay

### Deploy

```bash
# Apply the dev overlay (lower replicas, dev config)
kubectl apply -k infra/k8s/overlays/dev

# Verify rollout
kubectl -n secure-support-hub rollout status deployment/api
kubectl -n secure-support-hub rollout status deployment/web

# Check pods
kubectl -n secure-support-hub get pods
```

### Port-forward for local testing

```bash
kubectl -n secure-support-hub port-forward svc/api 8080:8080 &
kubectl -n secure-support-hub port-forward svc/web 5173:80 &
```

---

## 3. Kubernetes — Production Overlay

### Deploy

```bash
# Set your image tags in the prod kustomization overlay before deploying
# infra/k8s/overlays/prod/kustomization.yaml → images section

kubectl apply -k infra/k8s/overlays/prod

# Monitor rollout
kubectl -n secure-support-hub rollout status deployment/api --timeout=5m
kubectl -n secure-support-hub rollout status deployment/web --timeout=5m
```

### Verify post-deploy

```bash
# Health
kubectl -n secure-support-hub exec -it deploy/api -- \
  wget -qO- http://localhost:8080/actuator/health

# Check logs for startup errors
kubectl -n secure-support-hub logs deploy/api --tail=50
```

---

## 4. Rollback Procedure

### Docker Compose

```bash
# Pull and start the previous image tag
# Edit docker-compose.yml to point to previous image, then:
docker compose -f infra/docker-compose/docker-compose.yml up -d
```

### Kubernetes

```bash
# Roll back to the previous ReplicaSet
kubectl -n secure-support-hub rollout undo deployment/api
kubectl -n secure-support-hub rollout undo deployment/web

# Verify rollback
kubectl -n secure-support-hub rollout status deployment/api
```

---

## 5. Database Migrations

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

## 6. Secret Rotation (JWT_SECRET)

Rotating the JWT secret invalidates all active user sessions.

1. Generate a new secret: `openssl rand -base64 64`
2. Update `JWT_SECRET` in the deployment environment (k8s Secret or `.env`)
3. Restart the API pods/containers
4. Inform users: all active sessions will be terminated; users must log in again
