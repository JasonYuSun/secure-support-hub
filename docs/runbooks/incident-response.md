# Incident Response Runbook

**Service**: Secure Support Hub  
**Owner**: Engineering Team  
**Last Updated**: 2026-02-26

---

## 1. Service is Down (API or Web)

### Symptoms
- `http://localhost:8080/actuator/health` returns non-200 or times out
- Frontend shows "Failed to fetch" / blank page

### Steps

1. **Check container status**
   ```bash
   docker compose -f infra/docker-compose/docker-compose.yml ps
   ```
   Look for containers in `Exit` state.

2. **View API logs**
   ```bash
   docker compose -f infra/docker-compose/docker-compose.yml logs --tail=100 api
   ```

3. **Restart the affected service**
   ```bash
   docker compose -f infra/docker-compose/docker-compose.yml restart api
   # or
   docker compose -f infra/docker-compose/docker-compose.yml restart web
   ```

4. **Verify health**
   ```bash
   curl http://localhost:8080/actuator/health
   # Expected: {"status":"UP"}
   ```

5. If the container keeps exiting, check for:
   - OOM: `docker stats`
   - Port conflicts: `lsof -i :8080`
   - Missing env vars (see `.env.example`)

---

## 2. Database Connectivity Issues

### Symptoms
- API logs contain `Connection refused` or `HikariPool` timeout errors
- Health endpoint returns `{"status":"DOWN","components":{"db":{"status":"DOWN"}}}`

### Steps

1. **Verify Postgres is running**
   ```bash
   docker compose -f infra/docker-compose/docker-compose.yml ps postgres
   ```

2. **Check Postgres logs**
   ```bash
   docker compose -f infra/docker-compose/docker-compose.yml logs postgres
   ```

3. **Test DB connection manually**
   ```bash
   docker compose -f infra/docker-compose/docker-compose.yml exec postgres \
     psql -U securehub -d securehub -c '\l'
   ```

4. **Restart Postgres** (non-destructive for persistent volume)
   ```bash
   docker compose -f infra/docker-compose/docker-compose.yml restart postgres
   ```

5. **If data is corrupt**, restore from backup:
   ```bash
   docker compose -f infra/docker-compose/docker-compose.yml exec -T postgres \
     psql -U securehub securehub < backup.sql
   ```

6. **Check connection pool**: In `application.yml`, the HikariCP pool has a 20-connection limit. Under high load, increase `spring.datasource.hikari.maximum-pool-size`.

---

## 3. High Error Rates / 5xx Responses

### Symptoms
- Prometheus metric `http_server_requests_seconds_count{status="5xx"}` rising
- Users reporting errors submitting support requests

### Steps

1. **Check structured logs for stack traces**
   ```bash
   docker compose -f infra/docker-compose/docker-compose.yml logs --tail=200 api | grep -i "error\|exception"
   ```

2. **Check Actuator metrics**
   ```bash
   curl http://localhost:8080/actuator/metrics/http.server.requests
   ```

3. **Identify pattern**: Is the error isolated to one endpoint? Check request correlation IDs (`X-Request-ID` header) in logs.

4. **Roll back the last deployment** if error rate spiked after a deploy:
   ```bash
   # See deployment runbook for rollback steps
   ```

5. **Scale up API service capacity**:

   ECS Fargate (primary cloud path):
   ```bash
   aws ecs update-service \
     --cluster <ecs-cluster-name> \
     --service <ecs-api-service-name> \
     --desired-count 3 \
     --region ap-southeast-2
   ```

6. **Open a Severity-1 incident** if P95 latency > 5s or error rate > 5% sustained for more than 5 minutes.

---

## 4. JWT / Authentication Issues

### Symptoms
- Users get 401 on all requests despite having logged in
- Logs show `JWT signature does not match` or `JWT expired`

### Steps

1. **Expired token**: Instruct users to log out and log back in. Tokens expire after 24 h.

2. **Secret rotation**: If `JWT_SECRET` was rotated in the environment, all existing tokens are invalid. Users must re-authenticate.
   - Verify the env var: `docker compose -f infra/docker-compose/docker-compose.yml exec api env | grep JWT_SECRET`

3. **Clock skew**: If the API container's clock is off, `iat`/`exp` validation fails.
   ```bash
   docker compose -f infra/docker-compose/docker-compose.yml exec api date
   ```
   Compare to host: `date`. If off by > 30s, sync NTP on the host.

---

## 5. Attachment / S3 Upload Failures

### Symptoms
- Frontend shows "Upload failed due to network/CORS issue" or similar error when attaching files.
- Users report that files are "stuck" returning 403.
- Database has accumulating `PENDING` attachments.

### Steps

1. **S3 CORS Failure**:
   - Verify the S3 bucket CORS configuration matches the `CORS_ALLOWED_ORIGINS` in the frontend.
   - Run `aws s3api get-bucket-cors --bucket securehub-attachments-local` (replace with actual bucket name) to check.

2. **Pre-signed URL Expired (403)**:
   - Check if the user is uploading very large files or has slow internet, as URLs expire after 5 minutes.
   - Instruct the user to retry to generate a fresh URL.

3. **Pending Orphan Files**:
   - If uploads to S3 succeed but the backend `confirm` endpoint is never called, the DB leaves rows in `PENDING` state.
   - An automated Spring `@Scheduled` job sweeps these after 1 hour (`ATTACHMENTS_PENDING_UPLOAD_MAX_AGE`).
   - If the cron job is failing, check the API logs for `cleanupOrphanedPendingAttachments` errors.
