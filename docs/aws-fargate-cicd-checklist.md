# AWS Fargate CI/CD Checklist (From Zero to MVP)

**Project**: Secure Support Hub  
**Target platform**: AWS ECS Fargate + RDS PostgreSQL + ECR + GitHub Actions  
**Audience**: Engineer with cloud background in GCP, new to AWS account bootstrap  
**Last updated**: 2026-02-28

---

## How to use this document

- Work top-to-bottom.
- Treat each checkbox as a gate.
- Stop at any failed step and fix before moving on.
- If you get blocked, copy the exact error and ask for help on that specific step.
- Scope note: this checklist currently covers `dev` environment only (no `prod` configuration in this document).

### Sensitive data baseline rules

- [x] Treat Terraform state as sensitive metadata (it may contain secret-adjacent values and infra internals).
- [x] Never commit plaintext secrets to git (`DB_PASSWORD`, `JWT_SECRET`, API keys, private keys, tokens).
- [x] Keep runtime secrets in AWS Secrets Manager / SSM Parameter Store; inject into ECS task definitions via `secrets`.
- [x] Do not print secret values in CI logs, app logs, or debugging output.

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
- [x] Confirm deployment environment scope: `dev` only.

---

## Phase 1: AWS account bootstrap (fresh account)

- [x] Enable MFA on root account.
- [x] Sign in to AWS Console as root and complete account hygiene:
  - billing alarm email
  - alternate contacts
  - root MFA
- [x] Set up IAM Identity Center (recommended, no long-lived access keys):
  - enable IAM Identity Center in your chosen home region
  - create an admin user in Identity Center
  - create/attach an admin permission set
  - assign that user to your AWS account
- [x] Sign out root and use IAM Identity Center user for day-to-day operations.
- [x] Enable AWS Budget with email alerts:
  - `80%`, `100%`, `120%` threshold alerts.
- [x] Enable CloudTrail (at least one multi-region trail).
- [x] Configure AWS CLI with SSO and verify:

```bash
aws configure sso --profile securehub
aws sts get-caller-identity --profile securehub
```

- [x] Set default region in profile:

```bash
aws configure set region ap-southeast-2 --profile securehub
```

---

## Phase 2: Local machine prerequisites

- [x] Install and verify:
  - `aws` CLI v2
  - `terraform` >= 1.6
  - `docker`
  - `jq`
  - `git`
- [x] Verify toolchain:

```bash
aws --version
terraform version
docker --version
jq --version
git --version
```

- [x] Verify local hygiene protections:
  - `.gitignore` excludes `.env*` (except examples), `.terraform/`, `*.tfstate*`
  - `.gitignore` excludes private key files (for example `*.pem`, `*.key`) and local credential exports
  - no plaintext secrets in shell history or shared notes
  - optional but recommended: pre-commit secret scan (`gitleaks` or equivalent)

---

## Phase 3: Repository readiness checks

- [x] Ensure backend and frontend CI are green in GitHub Actions.
- [x] Ensure local verification passes:

```bash
make verify
```

- [x] Confirm API exposes `/actuator/health` in cloud profile.
- [x] Confirm DB migrations are idempotent and startup-safe.

---

## Phase 4: Terraform foundation (state and structure)

- [x] Create `infra/terraform/` if not present.
- [x] Define this structure:
  - `infra/terraform/bootstrap` (for TF state infrastructure only)
  - `infra/terraform/modules/network`
  - `infra/terraform/modules/ecr`
  - `infra/terraform/modules/rds`
  - `infra/terraform/modules/ecs`
  - `infra/terraform/modules/alb`
  - `infra/terraform/envs/dev`
- [x] Bootstrap remote Terraform state resources first (local state for bootstrap):
  - create S3 bucket for Terraform state
  - create DynamoDB table for state locking
  - apply once from `infra/terraform/bootstrap`
- [x] Harden Terraform state storage:
  - enable S3 Block Public Access (all on)
  - enable S3 default encryption (SSE-KMS preferred)
  - enable S3 versioning for state recovery
  - limit S3/DynamoDB IAM access to Terraform roles only
- [x] Confirm no sensitive values are exposed through Terraform outputs in CI logs.
- [x] Mark Terraform variables/outputs as `sensitive = true` where applicable to reduce accidental log exposure.
- [x] After bootstrap exists, add backend config in `envs/dev` pointing to that S3 bucket and DynamoDB table.
- [x] Create `terraform.tfvars` for `dev`.
- [x] Run formatting and validation:

```bash
cd infra/terraform/envs/dev
terraform init
terraform fmt -recursive
terraform validate
```

---

## Phase 5: Provision AWS infrastructure (dev)

### 5.1 Networking

- [x] Use `dev` networking profile:
  - ALB + ECS tasks in public subnets, tasks get public IP, RDS remains private, no NAT gateway
- [x] Create VPC/subnets/route tables according to selected profile.
- [x] Cost note:
  - NAT Gateway has fixed hourly + data processing cost
  - Public IPv4 addresses are also billed hourly
  - track both before choosing
- [x] Security groups:
  - ALB SG: allow inbound `80/443` from internet
  - ECS SG: allow inbound app ports from ALB SG only
  - RDS SG: allow inbound `5432` from ECS SG only

### 5.2 Container registry (ECR)

- [x] Create ECR repo: `secure-support-hub-api`
- [x] Create ECR repo: `secure-support-hub-web`
- [x] Enable image scan on push.
- [x] Add lifecycle policy to remove old untagged images.

### 5.3 Database (RDS PostgreSQL)

- [x] Create PostgreSQL RDS in private subnets.
- [x] Disable public accessibility.
- [x] Enable automated backups (minimum 7 days).
- [x] Store DB credentials in AWS Secrets Manager.
- [x] Confirm connectivity only from ECS SG.

### 5.4 ECS Fargate

- [x] Create ECS cluster.
- [x] Create CloudWatch log groups (`/ecs/securehub-api`, `/ecs/securehub-web`).
- [x] Create task execution role and task role.
- [x] Define API task definition:
  - image from ECR
  - env vars (non-secret)
  - secrets from Secrets Manager/SSM
  - health check path for container
- [x] Define Web task definition:
  - image from ECR
  - env var for API base URL
- [x] Define ECS services with desired count >= 1.

### 5.5 Load balancing and routing

- [x] Create ALB.
- [x] Create target groups for `web` and `api`.
- [x] Configure listener rules:
  - `/api/*` -> API target group
  - default `/` -> Web target group
- [x] Use `dev` access mode:
  - HTTP on ALB DNS name (no custom domain, no ACM public cert in this checklist)

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

- [x] Define required API env vars in ECS task:
  - `DB_URL`
  - `DB_USERNAME`
  - `DB_PASSWORD`
  - `JWT_SECRET`
  - `JWT_EXPIRATION_MS`
  - `CORS_ALLOWED_ORIGINS`
- [x] Define required Web env/build args:
  - `VITE_API_URL` (or your standardized final name)
- [x] Ensure no sensitive value is passed to frontend build args/env (anything in frontend build can become public).
- [x] Ensure CORS contains your deployed Web URL.
- [x] Ensure JWT secret length and entropy are sufficiently strong for internet-exposed deployment.
- [x] Classify and route values correctly:
  - sensitive (`DB_PASSWORD`, `JWT_SECRET`, third-party API keys) -> Secrets Manager / SSM Parameter Store + ECS `secrets`
  - non-sensitive (`CORS_ALLOWED_ORIGINS`, `JWT_EXPIRATION_MS`, `VITE_API_URL`) -> env vars/GitHub Variables
- [x] Ensure app runtime and startup logs never print sensitive values.

---

## Phase 7: GitHub Actions CD with AWS OIDC (no static keys)

### 7.1 AWS side

- [x] Define GitHub OIDC provider and deploy IAM role in Terraform (recommended), not manual console edits.
- [x] Apply Terraform and verify OIDC provider + deploy role exist in IAM.
- [x] Restrict trust policy by:
  - your GitHub org/user
  - repository name
  - branch (`main`) or environment
- [x] Create runtime secret entries per environment (`dev` first):
  - Secrets Manager: `DB_PASSWORD`, any third-party API keys
  - SSM Parameter Store (SecureString): `JWT_SECRET`
- [x] Attach least-privilege permissions for:
  - ECR push
  - ECS service update
  - task definition registration
  - CloudWatch logs read (optional)
  - `secretsmanager:GetSecretValue` (scoped to only required secret ARNs)
  - `ssm:GetParameters` (scoped to only required SSM parameter ARNs)
  - `kms:Decrypt` (scoped to only required KMS keys, if customer-managed keys are used)
- [x] Enforce ECS task definition rules:
  - sensitive values must be passed via ECS `secrets` using secret ARNs
  - do not place runtime secrets in plaintext ECS `environment`

### 7.2 GitHub side

- [x] Configure GitHub environment (`dev`).
- [x] Add GitHub Environment Variables (non-sensitive only):
  - `AWS_REGION=ap-southeast-2`
  - `ECR_API_REPOSITORY`
  - `ECR_WEB_REPOSITORY`
  - `ECS_CLUSTER`
  - `ECS_SERVICE_API`
  - `ECS_SERVICE_WEB`
  - `ECS_TASK_FAMILY_API`
  - `ECS_TASK_FAMILY_WEB`
- [x] Apply GitHub Secrets policy:
  - do not store long-lived runtime app secrets (`DB_PASSWORD`, `JWT_SECRET`, API keys) in GitHub
  - only use GitHub Secrets for CI/deploy-time values if unavoidable
- [x] Enforce repository safety rule:
  - no hardcoded sensitive values in workflow YAML
  - no hardcoded sensitive values in committed task-definition templates/files
- [x] Enable repository secret leak controls:
  - enable GitHub Secret Scanning / Push Protection if available
  - otherwise add a CI secret-scan step (for example `gitleaks`) on PR and `main`
- [x] Add workflow permissions:
  - `id-token: write`
  - `contents: read`

### 7.3 Workflow behavior

- [x] Keep existing PR checks as quality gates.
- [x] Add deploy workflow on push to `main`:
  - rely on PR quality gates for tests; deploy workflow focuses on build/release
  - build Docker images
  - tag with `sha`
  - push to ECR
  - render/register new task definitions using secret ARN references only
  - update ECS services
  - runtime secrets are resolved by ECS from Secrets Manager / SSM (not injected from workflow plaintext env)
  - wait for service stability
  - ~~run smoke tests~~ (skipped for MVP)
- [ ] Validate generated task definition artifact contains no plaintext secret values before deploy.
- [x] Add log hygiene in workflow steps:
  - avoid `echo`-ing secret-related env vars
  - use GitHub masking for any unavoidable sensitive runtime string
- [x] Add manual deploy trigger (`workflow_dispatch`) for rollback/redeploy.

### 7.4 Terraform pipeline (IaC)

- [x] Add a dedicated Terraform workflow (separate from app CD), e.g. `.github/workflows/terraform.yml`.
- [x] Trigger workflow on:
  - pull requests touching `infra/terraform/**`
  - pushes to `main` touching `infra/terraform/**`
  - manual `workflow_dispatch`
- [x] PR checks (`plan` only, no mutation):
  - `terraform fmt -check -recursive`
  - `terraform init`
  - `terraform validate`
  - `terraform plan` for `dev`
  - publish only sanitized plan summary (resource add/change/destroy counts), not raw plan with values
- [x] Main branch apply flow (`dev` first):
  - require GitHub Environment protection/approval for `dev`
  - run `terraform plan -out=tfplan`
  - run `terraform apply tfplan`
  - fail fast on lock contention (DynamoDB lock) instead of forcing unlock in pipeline
- [ ] Use OIDC with a dedicated Terraform IAM role (separate from app deploy role), least privilege scoped to Terraform-managed resources. (Skipped for MVP)
- [x] Add workflow `concurrency` guard to prevent parallel `apply` runs against the same environment/state.
- [x] Keep sensitive runtime values out of Terraform code and workflow YAML:
  - reference Secrets Manager ARNs/paths
  - no plaintext `DB_PASSWORD`, `JWT_SECRET`, API keys in repo
- [x] In CI logs, never print secret-bearing env vars or full rendered task definitions; mask unavoidable values via workflow masking.

### 7.5 Phase 7 validation checklist

- [x] Validate no plaintext secret literals in workflows/task definitions:

```bash
rg -n "DB_PASSWORD|JWT_SECRET|API_KEY|SECRET_KEY" .github/workflows infra || true
```

- [x] Validate no high-risk secret patterns in full repo history/working tree:

```bash
# Example (if using gitleaks)
gitleaks detect --source . --no-git
```

- [ ] Confirm ECS running task can read required secrets:
  - service reaches stable state
  - `/actuator/health` is `UP`
- [ ] Confirm secret rotation path:
  - update value in Secrets Manager
  - trigger ECS new deployment (or force new deployment)
  - verify application health after rotation
- [ ] Negative test:
  - remove secret-read permission (`secretsmanager:GetSecretValue` or `kms:Decrypt`)
  - confirm deployment/startup fails as expected

### 7.6 Contract and assumptions

- [x] Confirm no application API/schema/interface changes are introduced by this hardening work.
- [x] Confirm deployment contract is explicit:
  - source of truth for runtime sensitive values is AWS Secrets Manager (for DB) and SSM Parameter Store (for JWT)
  - GitHub Variables are non-sensitive metadata only
  - OIDC is the only AWS auth path for GitHub Actions (no static AWS access keys)
- [x] Confirm assumptions remain true:
  - repository is public
  - runtime target is ECS Fargate in `ap-southeast-2`
  - this checklist scope is `dev` only

---

## Phase 8: First deployment runbook (dev)

- [ ] Merge code to `main` after checks pass.
- [ ] Confirm deploy workflow starts.
- [ ] Confirm ECR image push succeeded.
- [ ] Confirm ECS new task revision is created.
- [ ] Confirm ECS service reaches stable state.
- [ ] Hit health endpoint:

```bash
curl -i http://<your-alb-dns-name>/actuator/health
```

- [ ] Open UI and verify login.
- [ ] Verify admin page visibility with admin account.
- [ ] Verify one end-to-end business flow:
  - login
  - create request
  - triage update

---

## Phase 9: Post-deploy dev operations controls

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
- [ ] Add security-focused observability:
  - alert on abnormal Secrets Manager access patterns (CloudTrail/EventBridge)
  - alert on sudden auth failure spikes and unusual request bursts
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

### 6) Suspected secret leak

- [x] Revoke/rotate exposed secret immediately in Secrets Manager.
- [x] Force ECS new deployment so new secret values are picked up by running tasks.
- [x] Invalidate active auth tokens/sessions if `JWT_SECRET` exposure is suspected.
- [x] Review CloudTrail and CI logs to identify blast radius and access timeline.
- [x] Open incident record and document root cause + prevention action.

---

### 5.6 Initial Deployment (Manual)

- [x] Push manual dummy/real images to ECR to allow ECS services to start successfully.
- [x] Apply Terraform to create ECS services and ALB.
- [x] Output ALB DNS name and verify it resolves.

---

## Definition of Done (MVP on AWS)

- [ ] All infra is Terraform-managed and reproducible.
- [ ] CI runs on PR and blocks merge on failure.
- [ ] CD deploys `main` to AWS dev automatically.
- [ ] App is reachable from public internet via ALB DNS (HTTP in current dev scope).
- [ ] API + Web health checks are green.
- [ ] Database backups are enabled.
- [ ] Logs and alarms are configured.
- [ ] Rollback procedure is tested at least once.

---

## Suggested next improvements after MVP

- [ ] Add blue/green deployments for ECS.
- [ ] Add WAF in front of ALB.
- [ ] Add dependency and container vulnerability scanning in CI.
- [ ] Add automated integration smoke tests after deploy.
- [ ] Add secret rotation schedule (JWT and DB credentials).
