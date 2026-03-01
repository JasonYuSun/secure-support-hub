import { test, expect, type Page } from '@playwright/test'
import { setupMockApi } from './support/mockApi'

async function login(page: Page, username = 'user', password = 'password') {
    await page.goto('/login')
    await page.getByLabel('Username').fill(username)
    await page.getByLabel('Password').fill(password)
    await page.getByRole('button', { name: /sign in/i }).click()
    await expect(page).toHaveURL('/')
}

test.describe('Attachment Flows', () => {
    test('upload, download, and delete request/comment attachments and delete request', async ({ page }) => {
        const mock = await setupMockApi(page)
        const requestId = mock.seedRequest({
            title: `Attachment Flow ${Date.now()}`,
            description: 'Attachment full flow test',
        })

        await login(page)
        await page.goto(`/requests/${requestId}`)

        const requestAttachmentInput = page.locator('.request-attachments-panel input[type="file"]')
        await requestAttachmentInput.setInputFiles({
            name: 'request-file.txt',
            mimeType: 'text/plain',
            buffer: Buffer.from('request attachment'),
        })

        await expect(page.getByText('request-file.txt')).toBeVisible()
        await expect(page.getByText(/ACTIVE/).first()).toBeVisible()

        const requestDownload = page.waitForRequest((req) =>
            req.url().includes(`/api/v1/requests/${requestId}/attachments/`) &&
            req.url().endsWith('/download-url'))
        await page.locator('.request-attachments-panel').getByRole('button', { name: /download/i }).first().click()
        await requestDownload

        page.once('dialog', (dialog) => dialog.accept())
        await page.locator('.request-attachments-panel').getByRole('button', { name: /^delete$/i }).first().click()
        await expect(
            page.locator('.request-attachments-panel .attachment-item-name', { hasText: 'request-file.txt' })
        ).toHaveCount(0)

        await page.locator('#comment-body').fill('Comment with file')
        await page.locator('#submit-comment-btn').click()
        await expect(page.getByText('Comment with file')).toBeVisible()

        const commentAttachmentInput = page.locator('.comment-attachments-panel input[type="file"]').first()
        await commentAttachmentInput.setInputFiles({
            name: 'comment-file.txt',
            mimeType: 'text/plain',
            buffer: Buffer.from('comment attachment'),
        })
        await expect(page.getByText('comment-file.txt')).toBeVisible()

        page.once('dialog', (dialog) => dialog.accept())
        await page.getByRole('button', { name: /delete request/i }).click()
        await expect(page).toHaveURL('/')
    })

    test('shows validation errors for bad type, oversize, and over-limit', async ({ page }) => {
        const mock = await setupMockApi(page)
        const validationRequestId = mock.seedRequest({
            title: `Validation ${Date.now()}`,
            description: 'Validation test',
        })

        await login(page)
        await page.goto(`/requests/${validationRequestId}`)

        const requestAttachmentInput = page.locator('.request-attachments-panel input[type="file"]')

        await requestAttachmentInput.setInputFiles({
            name: 'bad.zip',
            mimeType: 'application/zip',
            buffer: Buffer.from('zip-content'),
        })
        await expect(page.getByText(/File type is not allowed/)).toBeVisible()

        await requestAttachmentInput.setInputFiles({
            name: 'large.txt',
            mimeType: 'text/plain',
            buffer: Buffer.alloc(10 * 1024 * 1024 + 1, 'a'),
        })
        await expect(page.getByText(/File is too large/)).toBeVisible()

        const overLimitRequestId = mock.seedRequest({
            title: `Over limit ${Date.now()}`,
            description: 'Over limit validation',
        })
        mock.seedRequestAttachments(overLimitRequestId, 10)
        await page.goto(`/requests/${overLimitRequestId}`)

        const overLimitInput = page.locator('.request-attachments-panel input[type="file"]')
        await overLimitInput.setInputFiles({
            name: 'extra.txt',
            mimeType: 'text/plain',
            buffer: Buffer.from('extra'),
        })
        await expect(page.getByText(/Attachment limit reached for request/)).toBeVisible()
    })

    test('shows expired-url style error when mocked pre-signed PUT fails', async ({ page }) => {
        const mock = await setupMockApi(page)
        mock.setNextPresignedPutFailureStatus(403)
        const requestId = mock.seedRequest({
            title: `Presigned Failure ${Date.now()}`,
            description: 'Simulate expired URL from mocked S3',
        })

        await login(page)
        await page.goto(`/requests/${requestId}`)

        const requestAttachmentInput = page.locator('.request-attachments-panel input[type="file"]')
        await requestAttachmentInput.setInputFiles({
            name: 'expired.txt',
            mimeType: 'text/plain',
            buffer: Buffer.from('expired url'),
        })

        await expect(page.getByText(/Upload URL expired or forbidden/)).toBeVisible()
    })
})
