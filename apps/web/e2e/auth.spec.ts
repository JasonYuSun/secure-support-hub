import { test, expect } from '@playwright/test'

const USER_CREDENTIALS = { username: 'user', password: 'password' }

test.describe('Authentication', () => {
    test('login with valid credentials redirects to dashboard', async ({ page }) => {
        await page.goto('/login')

        await page.getByLabel('Username').fill(USER_CREDENTIALS.username)
        await page.getByLabel('Password').fill(USER_CREDENTIALS.password)
        await page.getByRole('button', { name: /sign in/i }).click()

        await expect(page).toHaveURL('/')
        await expect(page.getByText('Support Requests')).toBeVisible()
    })

    test('login with invalid credentials shows error message', async ({ page }) => {
        await page.goto('/login')

        await page.getByLabel('Username').fill('user')
        await page.getByLabel('Password').fill('wrongpassword')
        await page.getByRole('button', { name: /sign in/i }).click()

        await expect(page.getByRole('alert')).toBeVisible()
        await expect(page.getByRole('alert')).toContainText(/invalid/i)
        await expect(page).toHaveURL('/login')
    })

    test('protected routes redirect to login when unauthenticated', async ({ page }) => {
        await page.goto('/')
        await expect(page).toHaveURL('/login')
    })

    test('logout clears session and redirects to login', async ({ page }) => {
        // Log in first
        await page.goto('/login')
        await page.getByLabel('Username').fill(USER_CREDENTIALS.username)
        await page.getByLabel('Password').fill(USER_CREDENTIALS.password)
        await page.getByRole('button', { name: /sign in/i }).click()
        await expect(page).toHaveURL('/')

        // Log out
        await page.getByRole('button', { name: /logout/i }).click()
        await expect(page).toHaveURL('/login')

        // Verify session is gone
        await page.goto('/')
        await expect(page).toHaveURL('/login')
    })
})
