import { test, expect, type Page } from '@playwright/test'
import { setupMockApi } from './support/mockApi'

async function login(page: Page, username = 'user', password = 'password') {
    await page.goto('/login')
    await page.getByLabel('Username').fill(username)
    await page.getByLabel('Password').fill(password)
    await page.getByRole('button', { name: /sign in/i }).click()
    await expect(page).toHaveURL('/')
}

test.describe('Support Requests', () => {
    test('create a new support request and view it in request detail', async ({ page }) => {
        await setupMockApi(page)
        await login(page)

        await page.getByRole('link', { name: /new request/i }).click()
        await expect(page).toHaveURL('/requests/new')

        const title = `E2E Test Request ${Date.now()}`
        await page.getByLabel('Title').fill(title)
        await page.getByLabel('Description').fill('This request was created by a Playwright E2E test.')
        await page.getByRole('button', { name: /submit/i }).click()

        await expect(page).toHaveURL(/\/requests\/\d+/)
        await expect(page.getByRole('heading', { name: title })).toBeVisible()
        await expect(page.getByText('This request was created by a Playwright E2E test.')).toBeVisible()
    })

    test('add a comment on a support request', async ({ page }) => {
        const mock = await setupMockApi(page)
        const requestId = mock.seedRequest({
            title: `Comment Test ${Date.now()}`,
            description: 'Request for comment E2E test.',
        })

        await login(page)
        await page.goto(`/requests/${requestId}`)

        const commentText = 'This is an E2E comment added by Playwright.'
        await page.locator('#comment-body').fill(commentText)
        await page.locator('#submit-comment-btn').click()

        await expect(page.getByText(commentText)).toBeVisible()
    })
})
