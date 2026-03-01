# E2E Test Runs

This file contains the audit log of all executed E2E test runs.

| Run ID           | Journey ID | Timestamp                 | Environment | Role   | Result | Notes                                                                            |
| ---------------- | ---------- | ------------------------- | ----------- | ------ | ------ | -------------------------------------------------------------------------------- |
| RUN-20260301-001 | J-001      | 2026-03-01T19:44:00+11:00 | Remote      | USER   | PASSED | ReqID 11; Request created and commented smoothly.                                |
| RUN-20260301-002 | J-002      | 2026-03-01T19:47:00+11:00 | Remote      | TRIAGE | FAILED | 500 error on /users API (BUG-001), missing UI controls on detail view (BUG-002). |
| RUN-20260301-003 | J-003      | 2026-03-01T19:50:00+11:00 | Remote      | ADMIN  | PASSED | Role management and guardrails succeed.                                          |
| RUN-20260301-004 | J-004      | 2026-03-01T19:54:00+11:00 | Remote      | USER   | PASSED | Negative path RBAC checks passed.                                                |
| RUN-20260301-005 | J-005      | 2026-03-01T19:58:00+11:00 | Remote      | TRIAGE | PASSED | Redirected from admin path correctly.                                            |
| RUN-20260301-006 | J-006      | 2026-03-01T20:00:00+11:00 | Remote      | ADMIN  | PASSED | Self-demotion correctly prevented by UI guardrails.                              |
| RUN-20260301-007 | J-007      | 2026-03-01T20:04:00+11:00 | Remote      | UNAUTH | PASSED | Unauthorized accesses redirected to /login.                                      |
| RUN-20260301-008 | J-008      | 2026-03-01T20:07:00+11:00 | Remote      | USER   | PASSED | Empty request form creation blocked by browser validation.                       |
| RUN-20260301-009 | J-009      | 2026-03-01T20:10:00+11:00 | Remote      | UNAUTH | PASSED | Invalid login credentials rejected with error message.                           |
| RUN-20260301-010 | J-010      | 2026-03-01T20:13:00+11:00 | Remote      | TRIAGE | PASSED | CLOSED status hides transition controls in UI.                                   |
| RUN-20260301-013 | J-013      | 2026-03-01T20:15:00+11:00 | Remote      | USER   | PASSED | Own request and comment successfully deleted.                                    |
