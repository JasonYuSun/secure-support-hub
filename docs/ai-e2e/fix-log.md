# Fix Log

Current Batch ID: BATCH-20260301-01
Last Updated: 2026-03-03T10:30:00+11:00

## Fix Index (UPSERT view)

| Fix ID | Bug ID | Files Changed | Change summary | Risks | Result |
| --- | --- | --- | --- | --- | --- |
| FIX-001 | BUG-001 | `apps/api/src/main/java/com/suncorp/securehub/controller/UserController.java`; `apps/api/src/main/java/com/suncorp/securehub/repository/UserRepository.java` | Aligned role filter typing and query path for `/api/v1/users?role=...` | Low-Medium (role parsing/compatibility) | VERIFIED |
| FIX-002 | BUG-002 | `apps/web/src/pages/RequestDetailPage.tsx` | Added/validated TRIAGE/ADMIN request-detail transition controls and restored detail-flow operability | Low (UI control visibility by role/state) | VERIFIED |

## FIX-001
- Fix ID: FIX-001
- Related Bug ID: BUG-001
- Files changed:
  - `apps/api/src/main/java/com/suncorp/securehub/controller/UserController.java`
  - `apps/api/src/main/java/com/suncorp/securehub/repository/UserRepository.java`
- Change summary: Updated role-filter handling so `/api/v1/users?role=TRIAGE` maps correctly to repository query and no longer fails with 500.
- Why this fix works: Controller/repository role type expectations are aligned, allowing valid role-filtered queries.
- Risks: Invalid role query values may still require strict validation messaging; monitor for bad-input behavior.
- Validation performed:
  - Backend compile/test pass referenced in run notes.
  - Remote regression via TRIAGE journey rerun (`RUN-BATCH-20260301-01-J-002`) confirmed assignee list loads.
- Result: VERIFIED

## FIX-002
- Fix ID: FIX-002
- Related Bug ID: BUG-002
- Files changed:
  - `apps/web/src/pages/RequestDetailPage.tsx`
- Change summary: Added/confirmed status transition actions for TRIAGE/ADMIN in request detail, and validated assignee/status operations in detail workflow.
- Why this fix works: Detail page now exposes valid role-gated transition controls, enabling full triage workflow in-context.
- Risks: Control rendering depends on request status and role state; regression risk around state matrix edge cases.
- Validation performed:
  - Regression rerun of J-002 after deployment showed assignment and status transitions succeed.
  - Related role/state checks remained green in J-004/J-005/J-010.
- Result: VERIFIED
