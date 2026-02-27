import { test, expect } from '@playwright/test'

const USER_CREDENTIALS = { username: 'user', password: 'password' }

async function login(page: import('@playwright/test').Page) {
    await page.goto('/login')
    await page.getByLabel('Username').fill(USER_CREDENTIALS.username)
    await page.getByLabel('Password').fill(USER_CREDENTIALS.password)
    await page.getByRole('button', { name: /sign in/i }).click()
    await expect(page).toHaveURL('/')
}

test.describe('Support Requests', () => {
    test('create a new support request and see it in the list', async ({ page }) => {
        await login(page)

        await page.getByRole('link', { name: /new request/i }).click()
        await expect(page).toHaveURL('/requests/new')

        const title = `E2E Test Request ${Date.now()}`
        await page.getByLabel('Title').fill(title)
        await page.getByLabel('Description').fill('This request was created by a Playwright E2E test.')
        await page.getByRole('button', { name: /submit/i }).click()

        // Should redirect to the detail page
        await expect(page).toHaveURL(/\/requests\/\d+/)

        // To see it in the list, navigate back to the dashboard
        await page.goto('/')

        // The new request should appear in the list
        await expect(page.getByText(title).first()).toBeVisible()
    })

    test('view a support request detail page', async ({ page }) => {
        await login(page)

        // Create a request first
        await page.getByRole('link', { name: /new request/i }).click()
        const title = `Detail View Test ${Date.now()}`
        await page.getByLabel('Title').fill(title)
        await page.getByLabel('Description').fill('Checking that the detail page shows correct data.')
        await page.getByRole('button', { name: /submit/i }).click()

        // We are automatically redirected to the detail page
        await expect(page).toHaveURL(/\/requests\/\d+/)

        // Detail page content
        await expect(page.getByRole('heading', { name: title })).toBeVisible()
        await expect(page.getByText('OPEN')).toBeVisible()
        await expect(page.getByText('Checking that the detail page shows correct data.')).toBeVisible()
    })

    test('add a comment on a support request', async ({ page }) => {
        await login(page)

        // Create a request
        await page.getByRole('link', { name: /new request/i }).click()
        const title = `Comment Test ${Date.now()}`
        await page.getByLabel('Title').fill(title)
        await page.getByLabel('Description').fill('Request for comment E2E test.')
        await page.getByRole('button', { name: /submit/i }).click()

        // Add a comment
        const commentText = 'This is an E2E comment added by Playwright.'
        await page.locator('#comment-body').fill(commentText)
        await page.locator('#submit-comment-btn').click()

        // Comment should appear in the thread
        await expect(page.getByText(commentText)).toBeVisible()
    })
})
