import { test, expect, type Page } from '@playwright/test'
import { setupMockApi } from './support/mockApi'

async function login(page: Page, username = 'user', password = 'password') {
    await page.goto('/login')
    await page.getByLabel('Username').fill(username)
    await page.getByLabel('Password').fill(password)
    await page.getByRole('button', { name: /sign in/i }).click()
    await expect(page).toHaveURL('/')
}

test.describe('AI Assist Features', () => {
    test('[J-014] request summary generation', async ({ page }) => {
        const mock = await setupMockApi(page)
        const requestId = mock.seedRequest({
            title: 'AI Summary Test',
            description: 'Test description for summary.',
        })

        await login(page, 'user')
        await page.goto(`/requests/${requestId}`)

        await expect(page.getByRole('heading', { name: 'AI Assist' })).toBeVisible()
        await page.getByRole('button', { name: 'Summarize' }).click()
        await page.getByRole('button', { name: 'Generate' }).click()
        await expect(page.getByText('[Stub AI Summary] Generated summary of the request.')).toBeVisible()
    })

    test('[J-015] apply 2 AI-suggested tags sequentially (existing + new) as TRIAGE', async ({ page }) => {
        const mock = await setupMockApi(page)
        const requestId = mock.seedRequest({
            title: 'AI Tag Test',
            description: 'Test description for tags.',
        })
        // Seed "billing" tag — mock suggest-tags will return this as existing (isNew=false)
        mock.seedTag('billing')

        // Login as TRIAGE who can also create new tags
        await login(page, 'triage')
        await page.goto(`/requests/${requestId}`)

        // Open AI tag suggestion panel
        await page.getByRole('button', { name: 'Suggest Tags' }).click()
        await page.getByRole('button', { name: 'Generate' }).click()

        // --- Apply existing tag (isNew=false, existingTagId set) ---
        const applyExistingBtn = page.locator('div').filter({ hasText: 'billing' }).getByRole('button', { name: 'Apply Tag' }).first()
        await expect(applyExistingBtn).toBeVisible()
        await applyExistingBtn.click()
        // Tag chip appears in the applied tags panel
        await expect(page.getByRole('button', { name: 'Remove tag billing' })).toBeVisible()

        // --- Apply new tag (isNew=true) ---
        // "ai-assistant" is always returned as isNew=true by the updated mock
        const createAndApplyBtn = page.locator('div').filter({ hasText: 'ai-assistant' }).getByRole('button', { name: 'Create & Apply' }).first()
        await expect(createAndApplyBtn).toBeVisible()
        await createAndApplyBtn.click()
        // Second tag chip also appears
        await expect(page.getByRole('button', { name: 'Remove tag ai-assistant' })).toBeVisible()

        // Both buttons must now show "Applied ✓" and be disabled
        // (Button text changes after apply, so we locate by the new text)
        await expect(
            page.locator('div').filter({ hasText: 'billing' }).getByRole('button', { name: 'Applied ✓' }).first()
        ).toBeDisabled()
        await expect(
            page.locator('div').filter({ hasText: 'ai-assistant' }).getByRole('button', { name: 'Applied ✓' }).first()
        ).toBeDisabled()
    })

    test('[J-015b] plain USER cannot see Suggest Tags button — RBAC gate in AiAssistPanel', async ({ page }) => {
        const mock = await setupMockApi(page)
        const requestId = mock.seedRequest({
            title: 'AI Tag RBAC Test',
            description: 'Testing role restriction on AI tag suggestion.',
        })

        // Login as plain USER (no TRIAGE/ADMIN role)
        await login(page, 'user')
        await page.goto(`/requests/${requestId}`)

        // AI Assist panel is visible (USER can still summarize + draft)
        await expect(page.getByRole('heading', { name: /AI Assist/i })).toBeVisible()
        await expect(page.getByRole('button', { name: 'Summarize' })).toBeVisible()
        await expect(page.getByRole('button', { name: 'Draft Response' })).toBeVisible()

        // "Suggest Tags" must NOT be visible for plain USER — this is the RBAC gate
        await expect(page.getByRole('button', { name: 'Suggest Tags' })).not.toBeVisible()
    })

    test('[J-016] draft response generation', async ({ page }) => {
        const mock = await setupMockApi(page)
        const requestId = mock.seedRequest({
            title: 'AI Draft Test',
            description: 'Test description for draft.',
        })

        await login(page, 'user')
        await page.goto(`/requests/${requestId}`)

        await page.getByRole('button', { name: 'Draft Response' }).click()
        await page.getByRole('textbox', { name: /Optional/i }).fill('say hello')
        await page.getByRole('button', { name: 'Generate Draft' }).click()
        await expect(page.locator('textarea').first()).toHaveValue('[Stub AI Draft] Generated draft response.')
    })
})
