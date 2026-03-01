# AI-Driven E2E Testing Guide

This document standardizes how to use an autonomous browser subagent (like a Large Language Model with browser control capabilities) to perform End-to-End (E2E) testing on the Secure Support Hub.

## 1. System Prompt for the LLM Agent

Copy and paste the following prompt to the AI agent to initiate a full test run of the application:

```text
You are an expert QA Automation Engineer. I need you to perform a full End-to-End (E2E) test of the Secure Support Hub web application using your browser subagent capabilities.

**Test Environment:**
- URL: http://securehub-dev-alb-975740166.ap-southeast-2.elb.amazonaws.com
- Available test accounts (username/password): 
  - Customer: `user` / `password`
  - Triage: `triage` / `password`
  - Admin: `admin` / `password`

**Instructions:**
1. Read Section 2 of `docs/prompts/e2e-testing-guide.md` to understand the Expected Behaviors and Test Suites for all three roles (Customer, Triage, Admin).
2. Execute Test Suite 1 (Customer Flow), Test Suite 2 (Triage Flow), and Test Suite 3 (Admin Flow) sequentially.
3. Take screenshots at the final validation step of each major action (e.g., after creating a ticket, changing a status, or changing a user role).
4. If a test fails, capture the browser console logs and network requests immediately to document the error.
5. After all suites are complete, append your findings to Section 3 ("Bug Log and Fix Record") of `docs/prompts/e2e-testing-guide.md`. Mark passing suites with ✅ and failing suites with ❌, detailing the exact reproduction steps for any bugs found.

Please begin execution now.
```

---

## 2. Expected Behaviors & Test Suites

The application consists of three primary roles: `USER`, `TRIAGE`, and `ADMIN`. The expected behaviors for each are defined below.

### Test Suite 1: Customer Flow (`USER` Role)
**Expected Behavior:** Customers can manage their own requests but cannot see others or change statuses.
1. **Login:** Log in with `user` / `password`. Verify redirection to Dashboard.
2. **Creation:** Click "New Request". Create a request titled "Customer E2E Test".
3. **Attachments:** Upload a test document to the request. Verify the UI updates and the file can be downloaded.
4. **Commenting:** Add a comment to the thread.
5. **Deletion:** Delete the uploaded attachment, then click "Delete Request" to delete the entire thread.
6. **Logout:** Verify successful logout.

### Test Suite 2: Triage Flow (`TRIAGE` Role)
**Expected Behavior:** Triage engineers can view all requests globally, change statuse, and manage threads, but cannot access admin settings.
1. **Login:** Log in with `triage` / `password`.
2. **Triage Queue:** Verify visibility of the global Triage Queue containing requests from multiple users.
3. **Status Workflow:** Click action buttons on a request to transition it `OPEN -> IN_PROGRESS -> RESOLVED`. Verify the status badges update correctly.
4. **Assignment:** **(KNOWN MISSING UI)**: Triage engineers should be able to assign requests to specific users.
5. **Thread Management:** Navigate into a request, add a comment, and delete the request securely. 
6. **Access Control:** Attempt to navigate to `/admin` in the URL bar manually. Verify it redirects or shows an access denied error.
7. **Logout:** Verify successful logout.

### Test Suite 3: Admin Flow (`ADMIN` Role)
**Expected Behavior:** Admins have all Triage privileges plus access to global user and system settings.
1. **Login:** Log in with `admin` / `password`.
2. **Admin Panel Access:** Verify a link or navigation method exists to reach an Admin Panel or User Management page.
3. **Role Management:** Attempt to view the list of registered users and change a standard user's role to TRIAGE.
4. **System Visibility:** Verify Admins can perform all actions in the Triage Queue.
5. **Logout:** Verify successful logout.

---

## 3. Bug Log and Fix Record

*This section is actively maintained by the AI agent during E2E test runs. When the agent completes the prompt from Section 1, it will document its findings here prior to attempting fixes in the codebase.*

### Test Run: 01 Mar 2026 (Initial Baseline)
- **Suite 1 (Customer Flow):** ✅ **PASS**. (Resolved previous Nginx caching bug `fix(web): prevent index.html caching`).
- **Suite 2 (Triage Flow):** ❌ **FAIL (Partial)**. Status transitions and thread management work perfectly. However, the React UI (`TriagePage.tsx`) lacks buttons or dropdowns for assigning requests, even though the backend API supports it.
- **Suite 3 (Admin Flow):** ❌ **FAIL (Not Implemented)**. The `ADMIN` role exists in the database and JWT tokens, but the React frontend completely lacks an Admin Panel, User Management UI, or role-switching functionality.

### Next Actions Scheduled:
1. Fix: Implement Assignment Dropdown UI in `TriagePage.tsx`.
2. Fix: Implement Admin Dashboard and User Role Management UI.
