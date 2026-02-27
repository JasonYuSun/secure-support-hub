# AWS Fargate CI/CD Checklist (From Zero to MVP)

**Project**: Secure Support Hub  
**Target platform**: AWS ECS Fargate + RDS PostgreSQL + ECR + GitHub Actions  
**Audience**: Engineer with cloud background in GCP, new to AWS account bootstrap  
**Last updated**: 2026-02-27

---

## How to use this document

- Work top-to-bottom.
- Treat each checkbox as a gate.
- Stop at any failed step and fix before moving on.
- If you get blocked, copy the exact error and ask for help on that specific step.

---

## Phase 0: Decide your MVP deployment shape

- [x] Confirm runtime: `ECS Fargate` (no EKS for MVP).
- [x] Confirm components for MVP:
  - `web` container (React static site served by Nginx)
  - `api` container (Spring Boot)
  - `rds` (PostgreSQL)
  - `alb` (public ingress)
  - `ecr` repos (`web`, `api`)
- [x] Confirm single AWS region for MVP: `ap-southeast-2`.
- [x] Confirm one environment first: `dev` (add `prod` after dev is stable).

---

## Phase 1: AWS account bootstrap (fresh account)

- [ ] Enable MFA on root account.
- [ ] Sign in to AWS Console as root and complete account hygiene:
  - billing alarm email
  - alternate contacts
  - root MFA
- [ ] Set up IAM Identity Center (recommended, no long-lived access keys):
  - enable IAM Identity Center in your chosen home region
  - create an admin user in Identity Center
  - create/attach an admin permission set
  - assign that user to your AWS account
- [ ] Sign out root and use IAM Identity Center user for day-to-day operations.
- [ ] Enable AWS Budget with email alerts:
  - `80%`, `100%`, `120%` threshold alerts.
- [ ] Enable CloudTrail (at least one multi-region trail).
- [ ] Configure AWS CLI with SSO and verify:

```bash
aws configure sso --profile securehub
aws sts get-caller-identity --profile securehub
```

- [ ] Set default region in profile:

```bash
aws configure set region ap-southeast-2 --profile securehub
```

---

## Phase 2: Local machine prerequisites

- [ ] Install and verify:
  - `aws` CLI v2
  - `terraform` >= 1.6
  - `docker`
  - `jq`
  - `git`
- [ ] Verify toolchain:

```bash
aws --version
terraform version
docker --version
jq --version
git --version
```

---

## Phase 3: Repository readiness checks

- [ ] Ensure backend and frontend CI are green in GitHub Actions.
- [ ] Ensure local verification passes:

```bash
make verify
```

- [ ] Confirm API exposes `/actuator/health` in cloud profile.
- [ ] Confirm DB migrations are idempotent and startup-safe.

---

## Phase 4: Terraform foundation (state and structure)

- [ ] Create `infra/terraform/` if not present.
- [ ] Define this structure:
  - `infra/terraform/bootstrap` (for TF state infrastructure only)
  - `infra/terraform/modules/network`
  - `infra/terraform/modules/ecr`
  - `infra/terraform/modules/rds`
  - `infra/terraform/modules/ecs`
  - `infra/terraform/modules/alb`
  - `infra/terraform/envs/dev`
- [ ] Bootstrap remote Terraform state resources first (local state for bootstrap):
  - create S3 bucket for Terraform state
  - create DynamoDB table for state locking
  - apply once from `infra/terraform/bootstrap`
- [ ] After bootstrap exists, add backend config in `envs/dev` pointing to that S3 bucket and DynamoDB table.
- [ ] Create `terraform.tfvars` for `dev`.
- [ ] Run formatting and validation:

```bash
cd infra/terraform/envs/dev
terraform init
terraform fmt -recursive
terraform validate
```

---

## Phase 5: Provision AWS infrastructure (dev)

### 5.1 Networking

- [ ] Choose one networking profile explicitly:
  - `cost-optimized dev`: ALB + ECS tasks in public subnets, tasks get public IP, RDS remains private, no NAT gateway
  - `production-like`: ALB in public subnets, ECS + RDS in private subnets, NAT gateway enabled
- [ ] Create VPC/subnets/route tables according to selected profile.
- [ ] Cost note:
  - NAT Gateway has fixed hourly + data processing cost
  - Public IPv4 addresses are also billed hourly
  - track both before choosing
- [ ] Security groups:
  - ALB SG: allow inbound `80/443` from internet
  - ECS SG: allow inbound app ports from ALB SG only
  - RDS SG: allow inbound `5432` from ECS SG only

### 5.2 Container registry (ECR)

- [ ] Create ECR repo: `secure-support-hub-api`
- [ ] Create ECR repo: `secure-support-hub-web`
- [ ] Enable image scan on push.
- [ ] Add lifecycle policy to remove old untagged images.

### 5.3 Database (RDS PostgreSQL)

- [ ] Create PostgreSQL RDS in private subnets.
- [ ] Disable public accessibility.
- [ ] Enable automated backups (minimum 7 days).
- [ ] Store DB credentials in AWS Secrets Manager.
- [ ] Confirm connectivity only from ECS SG.

### 5.4 ECS Fargate

- [ ] Create ECS cluster.
- [ ] Create CloudWatch log groups (`/ecs/securehub-api`, `/ecs/securehub-web`).
- [ ] Create task execution role and task role.
- [ ] Define API task definition:
  - image from ECR
  - env vars (non-secret)
  - secrets from Secrets Manager/SSM
  - health check path for container
- [ ] Define Web task definition:
  - image from ECR
  - env var for API base URL
- [ ] Define ECS services with desired count >= 1.

### 5.5 Load balancing and routing

- [ ] Create ALB.
- [ ] Create target groups for `web` and `api`.
- [ ] Configure listener rules:
  - `/api/*` -> API target group
  - default `/` -> Web target group
- [ ] Decide TLS mode:
  - `dev-only`: use HTTP on ALB DNS name (no custom domain, no ACM public cert)
  - `prod-ready`: use HTTPS with ACM public cert
- [ ] If using HTTPS, verify domain prerequisites first:
  - own a public domain
  - create DNS validation records (Route53 or external DNS provider)
  - attach ACM cert to ALB listener `443`
  - redirect `80 -> 443`
- [ ] (Optional) Manage DNS in Route53 (required only if you choose Route53 as DNS host).

### 5.6 Apply and verify infra

- [ ] Apply Terraform:

```bash
cd infra/terraform/envs/dev
terraform plan -out tfplan
terraform apply tfplan
```

- [ ] Save outputs (`alb_dns_name`, `ecr_repo_urls`, `rds_endpoint`, `ecs_cluster_name`).

---

## Phase 6: Application runtime configuration mapping

- [ ] Define required API env vars in ECS task:
  - `DB_URL`
  - `DB_USERNAME`
  - `DB_PASSWORD`
  - `JWT_SECRET`
  - `JWT_EXPIRATION_MS`
  - `CORS_ALLOWED_ORIGINS`
- [ ] Define required Web env/build args:
  - `VITE_API_URL` (or your standardized final name)
- [ ] Ensure CORS contains your deployed Web URL.
- [ ] Ensure JWT secret length and entropy are production-safe.

---

## Phase 7: GitHub Actions CD with AWS OIDC (no static keys)

### 7.1 AWS side

- [ ] Define GitHub OIDC provider and deploy IAM role in Terraform (recommended), not manual console edits.
- [ ] Apply Terraform and verify OIDC provider + deploy role exist in IAM.
- [ ] Restrict trust policy by:
  - your GitHub org/user
  - repository name
  - branch (`main`) or environment
- [ ] Attach least-privilege permissions for:
  - ECR push
  - ECS service update
  - task definition registration
  - CloudWatch logs read (optional)

### 7.2 GitHub side

- [ ] Configure GitHub environments (`dev`, later `prod`).
- [ ] Add repository/environment variables:
  - `AWS_REGION=ap-southeast-2`
  - `ECR_API_REPOSITORY`
  - `ECR_WEB_REPOSITORY`
  - `ECS_CLUSTER`
  - `ECS_SERVICE_API`
  - `ECS_SERVICE_WEB`
  - `ECS_TASK_FAMILY_API`
  - `ECS_TASK_FAMILY_WEB`
- [ ] Add workflow permissions:
  - `id-token: write`
  - `contents: read`

### 7.3 Workflow behavior

- [ ] Keep existing PR checks as quality gates.
- [ ] Add deploy workflow on push to `main`:
  - run tests
  - build Docker images
  - tag with `sha`
  - push to ECR
  - render/register new task definitions
  - update ECS services
  - wait for service stability
  - run smoke tests
- [ ] Add manual deploy trigger (`workflow_dispatch`) for rollback/redeploy.

---

## Phase 8: First deployment runbook (dev)

- [ ] Merge code to `main` after checks pass.
- [ ] Confirm deploy workflow starts.
- [ ] Confirm ECR image push succeeded.
- [ ] Confirm ECS new task revision is created.
- [ ] Confirm ECS service reaches stable state.
- [ ] Hit health endpoint:

```bash
curl -i <http-or-https>://<your-domain-or-alb>/actuator/health
```

- [ ] Open UI and verify login.
- [ ] Verify admin page visibility with admin account.
- [ ] Verify one end-to-end business flow:
  - login
  - create request
  - triage update

---

## Phase 9: Post-deploy production readiness controls

- [ ] Enable CloudWatch alarms:
  - ALB 5xx
  - API task CPU high
  - API task memory high
  - ECS service running task count below desired
  - RDS CPU/storage/connections
- [ ] Set log retention policies (do not keep forever by default).
- [ ] Add dashboard with:
  - request rate
  - error rate
  - p95 latency
- [ ] Define rollback SOP and test once in dev.
- [ ] Set cost explorer monthly review reminder.

---

## Phase 10: Rollback checklist

- [ ] Identify last known good image tag.
- [ ] Update ECS service to previous task definition revision.
- [ ] Wait for service stability.
- [ ] Re-run smoke tests.
- [ ] Announce incident status and recovery notes.

---

## Common issues and fixes

### 1) ECS tasks keep restarting

- [ ] Check container logs in CloudWatch.
- [ ] Verify env vars and secrets exist.
- [ ] Verify DB connectivity and SG rules.
- [ ] Verify Flyway migration is not failing at startup.

### 2) UI loads but API calls fail (401/403/CORS)

- [ ] Check `CORS_ALLOWED_ORIGINS` includes exact frontend URL.
- [ ] Check frontend API env var (`VITE_API_URL`) matches deployed API path.
- [ ] Re-login to refresh stale role data in local storage.

### 3) ALB health check failing

- [ ] Verify target group health check path and port.
- [ ] Confirm app listens on expected container port.
- [ ] Confirm SG allows ALB -> ECS traffic.

### 4) GitHub Actions cannot assume AWS role

- [ ] Check IAM OIDC provider exists.
- [ ] Check role trust policy `sub` matches repo and branch.
- [ ] Ensure workflow has `id-token: write`.

### 5) Cannot connect API to RDS

- [ ] Confirm RDS in private subnets.
- [ ] Confirm ECS tasks networking matches your chosen profile (public IP or NAT-backed private).
- [ ] Confirm RDS SG allows inbound from ECS SG only.
- [ ] Confirm secret values and JDBC URL are correct.

---

## Definition of Done (MVP on AWS)

- [ ] All infra is Terraform-managed and reproducible.
- [ ] CI runs on PR and blocks merge on failure.
- [ ] CD deploys `main` to AWS dev automatically.
- [ ] App is reachable over HTTPS.
- [ ] API + Web health checks are green.
- [ ] Database backups are enabled.
- [ ] Logs and alarms are configured.
- [ ] Rollback procedure is tested at least once.

---

## Suggested next improvements after MVP

- [ ] Add `prod` environment with manual approval gates.
- [ ] Add blue/green deployments for ECS.
- [ ] Add WAF in front of ALB.
- [ ] Add dependency and container vulnerability scanning in CI.
- [ ] Add automated integration smoke tests after deploy.
- [ ] Add secret rotation schedule (JWT and DB credentials).
