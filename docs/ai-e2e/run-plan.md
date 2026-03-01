# E2E Run Plan

Global Configuration:
- Environment URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Timeout: 30000ms
- Network Idle Wait for key actions.

### P0 Journeys (Happy Paths)

**J-001 (USER)**
- **Action**: Go to /login.
- **Action**: Enter `user` / `password`, click login.
- **Assertion**: URL ends with `/`, "My Requests" text is visible.
- **Action**: Click "Create Request" (or go to /requests/new).
- **Action**: Fill text fields and submit.
- **Assertion**: URL pattern is `/requests/{id}`. Success message visible.
- **Action**: Enter text in comment box, click submit.
- **Assertion**: Comment text appears in request timeline.

**J-002 (TRIAGE)**
- **Action**: Go to /login, `triage` / `password`.
- **Assertion**: URL is `/`, "Triage Queue" text/nav visible.
- **Action**: Click on a request row.
- **Assertion**: URL is `/requests/{id}`.
- **Action**: Change assignee to self via dropdown.
- **Assertion**: Network PATCH 200, UI reflects new assignee.
- **Action**: Change status to IN_PROGRESS.
- **Assertion**: Network PATCH 200, status badge updates.

**J-003 (ADMIN)**
- **Action**: Go to /login, `admin` / `password`.
- **Assertion**: URL is `/`, "User Admin" nav visible.
- **Action**: Navigate to /admin/users.
- **Assertion**: User list is visible.
- **Action**: Click edit role on a user (e.g. 'user'). Change to TRIAGE.
- **Assertion**: UI reflects new role immediately. (Optionally switch it back to avoid ruining other tests, or pick a dummy user).

### P1 Journeys (Negative / Edge Cases)

**J-004 (RBAC USER)**
- **Action**: Log in as `user`.
- **Action**: Navigate to /triage.
- **Assertion**: Redirected to `/`.
- **Action**: Navigate to /admin/users.
- **Assertion**: Redirected to `/`.
- **Action**: Go to `/requests/{id}`.
- **Assertion**: Assignee and Status dropdowns are NOT visible/disabled.

**J-005 (RBAC TRIAGE)**
- **Action**: Log in as `triage`.
- **Action**: Navigate to /admin/users.
- **Assertion**: Redirected to `/`.

**J-006 (Admin Guardrail)**
- **Action**: Log in as `admin`.
- **Action**: Go to /admin/users.
- **Action**: Find row for 'admin'.
- **Assertion**: Role selector dropdown is disabled.

**J-007 (Unauth Redirects)**
- **Action**: Open incognito/clear session.
- **Action**: Go to `/`.
- **Assertion**: Redirected to `/login`.
- **Action**: Go to `/triage`.
- **Assertion**: Redirected to `/login`.

**J-008 (Validation - Create)**
- **Action**: Log in as `user`.
- **Action**: Go to /requests/new.
- **Action**: Submit form with empty fields.
- **Assertion**: HTML5 or UI validation text appears (e.g. "required"). Request is not created.

**J-009 (Validation - Login)**
- **Action**: Go to /login.
- **Action**: Enter `invalid` / `wrongpwd`.
- **Assertion**: Error text visible (e.g. "Invalid credentials"). URL remains /login.

**J-010 (State Machine - Triage)**
- **Action**: Log in as `triage`.
- **Action**: Create or find a request and set it to CLOSED.
- **Assertion**: Form to update status is disabled, or attempting to submit returns error.

**J-013 (Deletion - User)**
- **Action**: Log in as `user`.
- **Action**: Open own request.
- **Action**: Click delete on a comment.
- **Assertion**: Comment disappears.
- **Action**: Click delete on the request.
- **Assertion**: Redirected to `/`, request no longer in list.

### P2 Journeys (Attachments)

**J-011 (Attachments Success)**
- **Action**: Log in as `user`.
- **Action**: Go to /requests/new. Add `text/plain` file under 10MiB. Submit.
- **Assertion**: File link appears in request view.
- **Action**: Add comment with valid attachment.
- **Assertion**: File link appears.
- **Action**: Click download.
- **Assertion**: Network checks `/download-url` request.
- **Action**: Click delete.
- **Assertion**: Attachment removed from UI.

**J-012 (Attachments Bound Checks)**
- **Action**: Try uploading `.exe` file.
- **Assertion**: UI says "File type is not allowed".
- **Action**: Try uploading >10MiB file.
- **Assertion**: UI says "File is too large".
- **Action**: Try uploading 11 files to a request.
- **Assertion**: UI says "Attachment limit reached".
