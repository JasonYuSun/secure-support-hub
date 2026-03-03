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

    test('[J-015] suggest and apply tags', async ({ page }) => {
        const mock = await setupMockApi(page)
        const requestId = mock.seedRequest({
            title: 'AI Tag Test',
            description: 'Test description for tags.',
        })
        mock.seedTag('billing')

        // Login as TRIAGE to apply tags
        await login(page, 'triage')
        await page.goto(`/requests/${requestId}`)

        await page.getByRole('button', { name: 'Suggest Tags' }).click()
        await page.getByRole('button', { name: 'Generate' }).click()
        const suggestTagBadge = page.locator('div').filter({ hasText: 'billing' }).getByRole('button', { name: 'Apply Tag' }).first()
        await expect(suggestTagBadge).toBeVisible()
        await suggestTagBadge.click()

        await expect(page.getByRole('button', { name: 'Remove tag billing' })).toBeVisible()
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
