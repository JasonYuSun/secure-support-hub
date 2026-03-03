import { test, expect, type Page } from '@playwright/test'
import { setupMockApi } from './support/mockApi'

async function loginAs(page: Page, username: string) {
    await page.goto('/login')
    await page.getByLabel('Username').fill(username)
    await page.getByLabel('Password').fill('password')
    await page.getByRole('button', { name: /sign in/i }).click()
    await expect(page).toHaveURL('/')
}

test.describe('Tagging — AJ-013: TRIAGE/ADMIN manage tag dictionary', () => {
    test('triage can create a tag and it appears in the tag list', async ({ page }) => {
        const mock = await setupMockApi(page)
        const requestId = mock.seedRequest({
            title: 'Tagged Request',
            description: 'Testing tag management',
        })

        await loginAs(page, 'triage')
        await page.goto(`/requests/${requestId}`)

        // Open tag dictionary management
        await page.getByText('Manage Tag Dictionary').click()

        // Create a tag
        const tagName = `NewTag-${Date.now()}`
        await page.locator('#new-tag-input').fill(tagName)
        await page.locator('#create-tag-btn').click()

        // Tag should appear in dictionary list
        await expect(page.locator('#tag-dictionary-list')).toContainText(tagName)
    })

    test('triage can delete a tag from the dictionary', async ({ page }) => {
        const mock = await setupMockApi(page)
        const requestId = mock.seedRequest({
            title: 'Tagged Request for Delete',
            description: 'Testing tag deletion',
        })
        const tagName = 'TagToDelete'
        mock.seedTag(tagName)

        await loginAs(page, 'triage')
        await page.goto(`/requests/${requestId}`)

        // Open dictionary management
        await page.getByText('Manage Tag Dictionary').click()

        // Tag should already be in the dictionary
        await expect(page.locator('#tag-dictionary-list')).toContainText(tagName)

        // Register dialog handler BEFORE clicking delete to avoid race condition
        page.once('dialog', (dialog) => dialog.accept())
        await page.getByRole('button', { name: `Delete tag ${tagName}` }).click()

        // Tag should disappear from dictionary
        await expect(page.locator('#tag-dictionary-list')).not.toContainText(tagName)
    })

    test('admin can create a tag', async ({ page }) => {
        const mock = await setupMockApi(page)
        const requestId = mock.seedRequest({
            title: 'Admin tag test',
            description: 'admin creates tag',
        })

        await loginAs(page, 'admin')
        await page.goto(`/requests/${requestId}`)

        await page.getByText('Manage Tag Dictionary').click()
        const tagName = `AdminCreated-${Date.now()}`
        await page.locator('#new-tag-input').fill(tagName)
        await page.locator('#create-tag-btn').click()

        await expect(page.locator('#tag-dictionary-list')).toContainText(tagName)
    })
})

test.describe('Tagging — AJ-014: apply/unapply tags on own request', () => {
    test('user can apply an existing tag to their own request', async ({ page }) => {
        const mock = await setupMockApi(page)
        const requestId = mock.seedRequest({
            title: 'My Request',
            description: 'User applies a tag',
        })
        const tagName = 'BillingIssue'
        mock.seedTag(tagName)

        await loginAs(page, 'user')
        await page.goto(`/requests/${requestId}`)

        // Select tag from dropdown and apply
        await page.locator('#tag-select').selectOption({ label: tagName })
        await page.locator('#apply-tag-btn').click()

        // Tag chip should appear in applied list
        await expect(page.locator('#applied-tags-list')).toContainText(tagName)
    })

    test('user can unapply a tag from their own request', async ({ page }) => {
        const mock = await setupMockApi(page)
        const requestId = mock.seedRequest({
            title: 'Unapply Test Request',
            description: 'User unapplies a tag',
        })
        const tagName = 'TagToRemove'
        mock.seedTag(tagName)

        // Pre-apply the tag by seeding the mock state — we do this by navigating to
        // the page and applying via UI first
        await loginAs(page, 'user')
        await page.goto(`/requests/${requestId}`)

        // Apply
        await page.locator('#tag-select').selectOption({ label: tagName })
        await page.locator('#apply-tag-btn').click()
        await expect(page.locator('#applied-tags-list')).toContainText(tagName)

        // Unapply via the X button on the chip
        await page.getByRole('button', { name: `Remove tag ${tagName}` }).click()

        // Tag chip should disappear
        await expect(page.locator('#applied-tags-list')).not.toContainText(tagName)
    })

    test('applied tags persist across page navigation', async ({ page }) => {
        const mock = await setupMockApi(page)
        const requestId = mock.seedRequest({
            title: 'Persist Tags Test',
            description: 'Applied tags should survive navigation',
        })
        const tagName = 'PersistedTag'
        mock.seedTag(tagName)

        await loginAs(page, 'user')
        await page.goto(`/requests/${requestId}`)

        // Apply tag
        await page.locator('#tag-select').selectOption({ label: tagName })
        await page.locator('#apply-tag-btn').click()
        await expect(page.locator('#applied-tags-list')).toContainText(tagName)

        // Navigate away and back
        await page.goBack()
        await page.goto(`/requests/${requestId}`)

        // Tag should still be there (mock state is per-test, so the mock API returns it)
        await expect(page.locator('#applied-tags-list')).toContainText(tagName)
    })
})

test.describe('Tagging — Forbidden: user cannot tag inaccessible requests', () => {
    test("user cannot access another user's request (mock now mirrors real backend 403)", async ({ page }) => {
        const mock = await setupMockApi(page)
        // Create a request owned by triage user (id=2)
        const triageRequestId = mock.seedRequest({
            title: 'Triage Owned Request',
            description: 'Plain user should not tag this',
            owner: { id: 2, username: 'triage', email: 'triage@example.com', roles: ['USER', 'TRIAGE'] },
        })
        const tagName = 'ForbiddenTag'
        mock.seedTag(tagName)

        // Login as plain USER (id=1)
        await loginAs(page, 'user')

        // Navigate to triage's request — mock returns 403 for GET /requests/{id} (mirrors real backend)
        await page.goto(`/requests/${triageRequestId}`)

        // The request detail page should show an error/not-found state since GET returned 403
        // The apply button is definitely not reachable
        const applyBtn = page.locator('#apply-tag-btn')
        await expect(applyBtn).not.toBeVisible()
    })
})
