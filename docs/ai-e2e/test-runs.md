# E2E Test Runs

Current Batch ID: BATCH-20260301-01
Last Updated: 2026-03-03T10:30:00+11:00
Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com

## Batch Summary (UPSERT view)

| Journey ID | Run ID                      | Latest Result | Last Timestamp            |
| ---------- | --------------------------- | ------------- | ------------------------- |
| J-001      | RUN-BATCH-20260301-01-J-001 | PASSED        | 2026-03-01T19:44:00+11:00 |
| J-002      | RUN-BATCH-20260301-01-J-002 | PASSED        | 2026-03-02T01:00:00+11:00 |
| J-003      | RUN-BATCH-20260301-01-J-003 | PASSED        | 2026-03-01T19:50:00+11:00 |
| J-004      | RUN-BATCH-20260301-01-J-004 | PASSED        | 2026-03-01T19:54:00+11:00 |
| J-005      | RUN-BATCH-20260301-01-J-005 | PASSED        | 2026-03-01T19:58:00+11:00 |
| J-006      | RUN-BATCH-20260301-01-J-006 | PASSED        | 2026-03-01T20:00:00+11:00 |
| J-007      | RUN-BATCH-20260301-01-J-007 | PASSED        | 2026-03-01T20:04:00+11:00 |
| J-008      | RUN-BATCH-20260301-01-J-008 | PASSED        | 2026-03-01T20:07:00+11:00 |
| J-009      | RUN-BATCH-20260301-01-J-009 | PASSED        | 2026-03-01T20:10:00+11:00 |
| J-010      | RUN-BATCH-20260301-01-J-010 | PASSED        | 2026-03-01T20:13:00+11:00 |
| J-011      | RUN-BATCH-20260301-01-J-011 | PASSED        | 2026-03-01T23:10:00+11:00 |
| J-012      | RUN-BATCH-20260301-01-J-012 | PASSED        | 2026-03-01T23:15:00+11:00 |
| J-013      | RUN-BATCH-20260301-01-J-013 | PASSED        | 2026-03-01T20:15:00+11:00 |
| J-014      | RUN-BATCH-20260301-01-J-014 | PASSED        | 2026-03-04T01:50:00+11:00 |
| J-015      | RUN-BATCH-20260301-01-J-015 | PASSED        | 2026-03-04T01:50:00+11:00 |
| J-016      | RUN-BATCH-20260301-01-J-016 | PASSED        | 2026-03-04T01:50:00+11:00 |

## Run Records

### RUN-BATCH-20260301-01-J-001
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-001
- Journey ID: J-001
- Timestamp: 2026-03-01T19:44:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: USER
- Result: PASSED
- Steps executed: Login as `user` -> create request via `/requests/new` -> open created detail page -> post comment.
- Expected: Request created and comment rendered in thread.
- Actual: Request created (ReqID 11), landed on request detail, comment displayed.
- Evidence: UI confirmed request detail route and visible comment; no blocking console/network error recorded.
- Notes: Baseline USER happy-path completed.

### RUN-BATCH-20260301-01-J-002
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-002
- Journey ID: J-002
- Timestamp: 2026-03-02T01:00:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: TRIAGE
- Result: PASSED
- Steps executed: Login as `triage` -> open `/triage` -> open request detail -> assign user -> transition status -> add comment -> regression rerun after fixes.
- Expected: Assignee dropdown loads users; status controls available and transitions succeed.
- Actual: Final rerun passed. Assignee and status controls functioned correctly end-to-end.
- Evidence: Initial failure evidence: `GET /api/v1/users?role=TRIAGE` returned 500 and controls were unavailable (mapped to BUG-001/BUG-002). Regression evidence: same journey re-executed with successful assignment/status update.
- Notes: This Run key was upserted from FAILED to PASSED after fixes.

### RUN-BATCH-20260301-01-J-003
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-003
- Journey ID: J-003
- Timestamp: 2026-03-01T19:50:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: ADMIN
- Result: PASSED
- Steps executed: Login as `admin` -> open `/admin/users` -> modify a non-admin user's role.
- Expected: User list visible; role update reflected in UI.
- Actual: Role management action succeeded and refreshed state was visible.
- Evidence: User Admin page accessible with role update interaction completed.
- Notes: Includes admin guardrail validation in separate journey J-006.

### RUN-BATCH-20260301-01-J-004
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-004
- Journey ID: J-004
- Timestamp: 2026-03-01T19:54:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: USER
- Result: PASSED
- Steps executed: Login as `user` -> navigate directly to `/triage` and `/admin/users`.
- Expected: USER cannot access privileged routes and is redirected.
- Actual: Redirect to `/` observed; privileged controls not available.
- Evidence: Route guard behavior observed in browser navigation.
- Notes: RBAC negative path passed.

### RUN-BATCH-20260301-01-J-005
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-005
- Journey ID: J-005
- Timestamp: 2026-03-01T19:58:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: TRIAGE
- Result: PASSED
- Steps executed: Login as `triage` -> navigate directly to `/admin/users`.
- Expected: TRIAGE is redirected away from ADMIN page.
- Actual: Redirected to `/`.
- Evidence: Browser URL redirection confirmed.
- Notes: RBAC enforcement for TRIAGE -> ADMIN route passed.

### RUN-BATCH-20260301-01-J-006
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-006
- Journey ID: J-006
- Timestamp: 2026-03-01T20:00:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: ADMIN
- Result: PASSED
- Steps executed: Login as `admin` -> open `/admin/users` -> inspect own row role selector.
- Expected: Self-demotion action unavailable for `admin` account.
- Actual: Selector/action disabled on own row.
- Evidence: Disabled UI control observed for `admin` row.
- Notes: Matches admin self-protection guardrail.

### RUN-BATCH-20260301-01-J-007
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-007
- Journey ID: J-007
- Timestamp: 2026-03-01T20:04:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: UNAUTH
- Result: PASSED
- Steps executed: Logged-out session -> navigate to `/` and `/triage`.
- Expected: Redirect to `/login` for protected routes.
- Actual: Both routes redirected to `/login`.
- Evidence: Browser URL and page state confirmed login screen.
- Notes: Auth route guard passed.

### RUN-BATCH-20260301-01-J-008
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-008
- Journey ID: J-008
- Timestamp: 2026-03-01T20:07:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: USER
- Result: PASSED
- Steps executed: Login as `user` -> open `/requests/new` -> attempt submit with missing required fields.
- Expected: Submission blocked and validation feedback shown.
- Actual: Browser/form validation prevented request creation.
- Evidence: No request created; required fields blocked submission.
- Notes: Client-side required-field validation confirmed.

### RUN-BATCH-20260301-01-J-009
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-009
- Journey ID: J-009
- Timestamp: 2026-03-01T20:10:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: UNAUTH
- Result: PASSED
- Steps executed: Navigate to `/login` -> submit invalid credentials.
- Expected: Login rejected with error and remain on login page.
- Actual: Invalid credential error displayed; no redirect to authenticated routes.
- Evidence: Visible login error message and unchanged auth state.
- Notes: Negative auth path passed.

### RUN-BATCH-20260301-01-J-010
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-010
- Journey ID: J-010
- Timestamp: 2026-03-01T20:13:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: TRIAGE
- Result: PASSED
- Steps executed: Login as TRIAGE -> open request in CLOSED state -> inspect transition actions.
- Expected: No invalid status transition action available from CLOSED.
- Actual: Transition controls hidden/absent for CLOSED state.
- Evidence: UI action area showed no forward transition for CLOSED.
- Notes: State-machine boundary behavior passed.

### RUN-BATCH-20260301-01-J-011
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-011
- Journey ID: J-011
- Timestamp: 2026-03-01T23:10:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: USER
- Result: PASSED
- Steps executed: Login as USER -> upload request attachment -> upload comment attachment -> download via UI -> delete attachment.
- Expected: Full attachment lifecycle succeeds; download invokes presigned `/download-url` flow.
- Actual: Upload/download/delete succeeded.
- Evidence: Attachment items visible with successful lifecycle; download presign flow triggered from UI.
- Notes: Remote S3-backed flow validated.

### RUN-BATCH-20260301-01-J-012
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-012
- Journey ID: J-012
- Timestamp: 2026-03-01T23:15:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: USER
- Result: PASSED
- Steps executed: Upload unsupported MIME and oversized files; test count boundary enforcement.
- Expected: Explicit boundary errors for invalid type, oversize, and over-limit.
- Actual: Invalid MIME message observed; boundary validation blocked unsupported upload.
- Evidence: UI displayed file-type rejection (`File type is not allowed`); other boundary checks completed in same pass.
- Notes: Boundary suite passed; specific message strings matched expected patterns.

### RUN-BATCH-20260301-01-J-013
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-013
- Journey ID: J-013
- Timestamp: 2026-03-01T20:15:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: USER
- Result: PASSED
- Steps executed: Login as USER -> open own request -> delete own comment -> delete own request.
- Expected: Comment and request removed; navigation returns to dashboard/list.
- Actual: Comment and request deletion succeeded.
- Evidence: Deleted entities no longer visible; route returned to dashboard view.
- Notes: Ownership-based deletion rules passed.

### RUN-BATCH-20260301-01-J-014
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-014
- Journey ID: J-014
- Timestamp: 2026-03-04T01:50:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: USER
- Result: PASSED
- Steps executed: Login as USER -> open request detail -> click Summarize Request -> verify output.
- Expected: AI Summary is generated and displayed without error.
- Actual: Summary generated and displayed successfully using the deployed AWS Bedrock backend.
- Evidence: Playwright/browser subagent recorded successful API call and UI rendering.
- Notes: Live Bedrock integration validated on remote environment.

### RUN-BATCH-20260301-01-J-015
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-015
- Journey ID: J-015
- Timestamp: 2026-03-04T01:50:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: TRIAGE
- Result: PASSED
- Steps executed: Login as TRIAGE -> open request detail -> click Suggest Tags -> click Apply tag badge -> verify tag applied visually.
- Expected: Tag is suggested and accurately applies to the request with clear visual confirmation ('Applied ✓').
- Actual: Final rerun passed. Tag applied successfully and button updated to indicate success.
- Evidence: Initial run revealed BUG-003 (Silent apply failure/no UI feedback). Regression run post-FIX-003 confirmed 'Applied ✓' state.
- Notes: Validated on remote environment; state UPSERTED from blocked/confusing to PASSED.

### RUN-BATCH-20260301-01-J-016
- Batch ID: BATCH-20260301-01
- Run ID: RUN-BATCH-20260301-01-J-016
- Journey ID: J-016
- Timestamp: 2026-03-04T01:50:00+11:00
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Role: USER
- Result: PASSED
- Steps executed: Login as USER -> open request detail -> type override prompt -> click Draft Response -> click Use Draft -> verify scrolling.
- Expected: Draft response is generated, fills the textarea, AND scrolls the user down to view the populated comment box.
- Actual: Final rerun passed. Draft populated and smooth-scrolling engaged correctly.
- Evidence: Initial test run revealed BUG-004 (Draft applied silently out of view). Regression run post-FIX-004 showed correct focus and scroll.
- Notes: Validated on remote environment; UX issues resolved.
