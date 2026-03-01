import { test, expect } from '@playwright/test'
import { setupMockApi } from './support/mockApi'

test.describe('Authentication', () => {
    test('login with valid credentials redirects to dashboard', async ({ page }) => {
        await setupMockApi(page)
        await page.goto('/login')

        await page.getByLabel('Username').fill('user')
        await page.getByLabel('Password').fill('password')
        await page.getByRole('button', { name: /sign in/i }).click()

        await expect(page).toHaveURL('/')
        await expect(page.getByText('My Requests')).toBeVisible()
    })

    test('login with invalid credentials shows error message', async ({ page }) => {
        await setupMockApi(page)
        await page.goto('/login')

        await page.getByLabel('Username').fill('user')
        await page.getByLabel('Password').fill('wrongpassword')
        await page.getByRole('button', { name: /sign in/i }).click()

        await expect(page.getByRole('alert')).toContainText(/invalid/i)
        await expect(page).toHaveURL('/login')
    })

    test('protected routes redirect to login when unauthenticated', async ({ page }) => {
        await setupMockApi(page)
        await page.goto('/')
        await expect(page).toHaveURL('/login')
    })
})
