# Bug Log

Current Batch ID: BATCH-20260301-01
Last Updated: 2026-03-03T10:30:00+11:00

## Bug Index (UPSERT view)

| Bug ID  | Journey ID | Title                                                              | Severity | Priority | Status   | Owner | First seen date | Last seen date | Occurrence count |
| ------- | ---------- | ------------------------------------------------------------------ | -------- | -------- | -------- | ----- | --------------- | -------------- | ---------------- |
| BUG-001 | J-002      | GET /api/v1/users?role=TRIAGE returned 500 during assignee load    | High     | P0       | VERIFIED | AI    | 2026-03-01      | 2026-03-01     | 1                |
| BUG-002 | J-002      | TRIAGE request detail missing effective assignment/status controls | Medium   | P1       | VERIFIED | AI    | 2026-03-01      | 2026-03-01     | 1                |
| BUG-003 | J-015      | AI Tags apply action lacks visual feedback                         | Medium   | P2       | VERIFIED | AI    | 2026-03-04      | 2026-03-04     | 1                |
| BUG-004 | J-016      | AI Draft Response apply action lacks scroll focus                  | Medium   | P2       | VERIFIED | AI    | 2026-03-04      | 2026-03-04     | 1                |

## BUG-001
- Bug ID: BUG-001
- Journey ID: J-002
- Title: GET /api/v1/users?role=TRIAGE returned 500 during assignee load
- Severity: High
- Priority: P0
- Repro steps:
  1. Login as `triage`.
  2. Open `/triage`.
  3. Open assignee dropdown for a request.
- Expected: Assignee dropdown is populated with TRIAGE/ADMIN assignable users.
- Actual: Assignee list failed to load; dropdown unusable beyond unassigned option.
- Evidence: Network failure on assignee lookup endpoint during TRIAGE flow.
- Request/response evidence: `GET /api/v1/users?role=TRIAGE` -> `500 Internal Server Error` (requestId not captured in run notes).
- Suspected root cause: Role-filter query path mismatch between controller parameter handling and repository signature.
- Affected layer: Backend/API
- Status: VERIFIED
- Owner: AI
- First seen date: 2026-03-01
- Last seen date: 2026-03-01
- Occurrence count: 1
- Verification evidence: `RUN-BATCH-20260301-01-J-002` rerun passed on 2026-03-02T01:00:00+11:00 after fix deployment.

## BUG-002
- Bug ID: BUG-002
- Journey ID: J-002
- Title: TRIAGE request detail missing effective assignment/status controls
- Severity: Medium
- Priority: P1
- Repro steps:
  1. Login as `triage`.
  2. Open a request detail page (`/requests/:id`).
  3. Attempt assignment and status transition from detail view.
- Expected: TRIAGE can assign request and perform valid status transitions from detail context.
- Actual: Detail flow was blocked/ineffective during initial run (assignee path broken; status control gap observed).
- Evidence: TRIAGE detail path could not complete intended assignment/status flow in first execution.
- Request/response evidence: Dependent assignee load endpoint failed during initial run; detail controls confirmed on later rerun.
- Suspected root cause: Combined effect of assignee API failure (BUG-001) and missing/insufficient transition controls in detail view implementation.
- Affected layer: Frontend
- Status: VERIFIED
- Owner: AI
- First seen date: 2026-03-01
- Last seen date: 2026-03-01
- Occurrence count: 1
- Verification evidence: `RUN-BATCH-20260301-01-J-002` rerun passed with working assignee + transition actions.

## BUG-003
- Bug ID: BUG-003
- Journey ID: J-015
- Title: AI Tags apply action lacks visual feedback
- Severity: Medium
- Priority: P2
- Repro steps:
  1. Login as `triage`.
  2. Open a request detail page (`/requests/:id`).
  3. Click 'Suggest Tags' and click 'Apply Tag' on a suggestion.
- Expected: The button disables and shows 'Applied' so the user knows the tag was correctly added to the request.
- Actual: The button briefly says 'Applying...' and reverts to 'Apply Tag', leaving the user confused whether the action succeeded.
- Evidence: User reported no visual indication of apply application.
- Request/response evidence: Tag API call succeeds but UI state doesn't reflect the success locally on the button.
- Suspected root cause: Missing state handling for successfully applied tags in `AiTagsCard`.
- Affected layer: Frontend
- Status: VERIFIED
- Owner: AI
- First seen date: 2026-03-04
- Last seen date: 2026-03-04
- Occurrence count: 1
- Verification evidence: Manual user report + E2E UX audit logic.

## BUG-004
- Bug ID: BUG-004
- Journey ID: J-016
- Title: AI Draft Response apply action lacks scroll focus
- Severity: Medium
- Priority: P2
- Repro steps:
  1. Login as `user`.
  2. Open a request detail page (`/requests/:id`).
  3. Click 'Draft Response' and click 'Use Draft'.
- Expected: The comment text area populates with the draft AND the user is scrolled to see it.
- Actual: The comment populates out of view, and the user assumes nothing happened when clicking 'Use Draft' since the text area is far below the AI Assist panel.
- Evidence: User reported the apply button appeared to do nothing.
- Request/response evidence: UI behavior inspection in `AiDraftCard`.
- Suspected root cause: `onUseDraft` populated the text but missing scroll-into-view behavior.
- Affected layer: Frontend
- Status: VERIFIED
- Owner: AI
- First seen date: 2026-03-04
- Last seen date: 2026-03-04
- Occurrence count: 1
- Verification evidence: Manual user report + UX review.
