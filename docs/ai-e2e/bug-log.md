# Bug Log

This file tracks all bugs discovered during E2E testing.

| Bug ID  | Journey ID | Title                                                   | Severity | Priority | Status      | Owner | Created    |
| ------- | ---------- | ------------------------------------------------------- | -------- | -------- | ----------- | ----- | ---------- |
| BUG-001 | J-002      | GET /api/v1/users?role=TRIAGE returns 500 error         | High     | P0       | FIXED_LOCAL | AI    | 2026-03-01 |
| BUG-002 | J-002      | Missing Assignee/Status controls on Request Detail view | Medium   | P1       | FIXED_LOCAL | AI    | 2026-03-01 |

## BUG-001: GET /api/v1/users?role=TRIAGE returns 500 error
- **Journey ID**: J-002
- **Severity**: High
- **Priority**: P0
- **Repro Steps**: 
  1. Login as TRIAGE.
  2. Go to /triage.
  3. Click Assignee dropdown on a request.
- **Expected**: Dropdown is populated with TRIAGE users.
- **Actual**: Dropdown only shows "--- Unassigned ---".
- **Evidence**: `GET /api/v1/users?role=TRIAGE` fails.
- **Request/Response Evidence**: Status: 500 Internal Server Error. Endpoint: `/api/v1/users?role=TRIAGE`. 
- **Suspected Root Cause**: Backend endpoint `/api/v1/users` doesn't support the `role` filter properly.
- **Root Cause & Fix**: `UserRepository.findByRoles_Name` parameter type changed from `String` to `Role.RoleName` enum. Fixed locally, compiles successfully. Pending deployment to remote.
- **Affected layer**: Backend/API
- **Status**: FIXED_LOCAL
- **Owner**: AI
- **Created Date**: 2026-03-01

## BUG-002: Missing Assignee/Status controls on Request Detail view
- **Journey ID**: J-002
- **Severity**: Medium
- **Priority**: P1
- **Repro Steps**:
  1. Login as TRIAGE. 
  2. Open a request detail view (`/requests/:id`).
- **Expected**: Controls to change status and assignee exist.
- **Actual**: Only read-only text is present. Updates must be made from the `/triage` list view.
- **Evidence**: UI inspection shows missing elements.
- **Root Cause & Fix**: `RequestDetailPage.tsx` was actually rendering `AssigneeSelect` but it failed invisibly due to BUG-001. `StatusSelect` was completely omitted from the design. Fixed BUG-001 to resolve assignee list, and explicitly added `STATUS_TRANSITIONS` buttons next to the Status Badge for Triage/Admin.
- **Affected layer**: Frontend
- **Status**: FIXED_LOCAL
- **Owner**: AI
- **Created Date**: 2026-03-01
