import { type Page, type Route } from '@playwright/test'

type Role = 'USER' | 'TRIAGE' | 'ADMIN'

type MockUser = {
    id: number
    username: string
    email: string
    roles: Role[]
}

type MockRequest = {
    id: number
    title: string
    description: string
    status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
    createdBy: MockUser
    assignedTo: MockUser | null
    createdAt: string
    updatedAt: string
}

type MockComment = {
    id: number
    requestId: number
    author: MockUser
    body: string
    createdAt: string
}

type MockAttachment = {
    id: number
    requestId: number | null
    commentId: number | null
    fileName: string
    contentType: string
    fileSize: number
    state: 'PENDING' | 'ACTIVE' | 'FAILED'
    uploadedBy: MockUser
    createdAt: string
}

const USERS: Record<string, MockUser> = {
    user: { id: 1, username: 'user', email: 'user@example.com', roles: ['USER'] },
    triage: { id: 2, username: 'triage', email: 'triage@example.com', roles: ['USER', 'TRIAGE'] },
    admin: { id: 3, username: 'admin', email: 'admin@example.com', roles: ['USER', 'TRIAGE', 'ADMIN'] },
}

const json = (route: Route, data: unknown, status = 200) =>
    route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(data),
    })

const pathSegments = (pathname: string): string[] =>
    pathname.replace(/^\/api\/v1\/?/, '').split('/').filter(Boolean)

type SetupOptions = {
    failNextPresignedPutStatus?: number
}

export type MockApiController = {
    seedRequest: (params: { title: string; description: string; owner?: MockUser }) => number
    seedRequestAttachments: (requestId: number, count: number) => void
    setNextPresignedPutFailureStatus: (status: number | null) => void
}

export async function setupMockApi(page: Page, options?: SetupOptions): Promise<MockApiController> {
    let nextRequestId = 100
    let nextCommentId = 500
    let nextAttachmentId = 900
    let failNextPresignedPutStatus = options?.failNextPresignedPutStatus ?? null

    const requests = new Map<number, MockRequest>()
    const commentsByRequest = new Map<number, MockComment[]>()
    const attachmentsByRequest = new Map<number, MockAttachment[]>()
    const attachmentsByComment = new Map<number, MockAttachment[]>()

    const nowIso = () => new Date().toISOString()

    const getActiveUserFromAuthHeader = (headerValue: string | null): MockUser => {
        const token = (headerValue ?? '').replace(/^Bearer\s+/i, '')
        const username = token.replace(/^mock-token-/, '')
        return USERS[username] ?? USERS.user
    }

    const buildRequestPage = (): Record<string, unknown> => {
        const content = Array.from(requests.values()).sort((a, b) => Number(b.id - a.id))
        return {
            content,
            totalElements: content.length,
            totalPages: 1,
            number: 0,
            size: 20,
        }
    }

    const buildCommentPage = (requestId: number): Record<string, unknown> => {
        const content = commentsByRequest.get(requestId) ?? []
        return {
            content,
            totalElements: content.length,
            totalPages: 1,
            number: 0,
            size: 50,
        }
    }

    const seedRequest = (params: { title: string; description: string; owner?: MockUser }): number => {
        const id = nextRequestId++
        const owner = params.owner ?? USERS.user
        const item: MockRequest = {
            id,
            title: params.title,
            description: params.description,
            status: 'OPEN',
            createdBy: owner,
            assignedTo: null,
            createdAt: nowIso(),
            updatedAt: nowIso(),
        }
        requests.set(id, item)
        commentsByRequest.set(id, [])
        attachmentsByRequest.set(id, [])
        return id
    }

    const seedRequestAttachments = (requestId: number, count: number) => {
        const req = requests.get(requestId)
        if (!req) return
        const arr = attachmentsByRequest.get(requestId) ?? []
        for (let i = 0; i < count; i += 1) {
            arr.push({
                id: nextAttachmentId++,
                requestId,
                commentId: null,
                fileName: `existing-${i + 1}.txt`,
                contentType: 'text/plain',
                fileSize: 128,
                state: 'ACTIVE',
                uploadedBy: req.createdBy,
                createdAt: nowIso(),
            })
        }
        attachmentsByRequest.set(requestId, arr)
    }

    await page.route('**/api/v1/**', async (route) => {
        const request = route.request()
        const url = new URL(request.url())
        const method = request.method()
        const seg = pathSegments(url.pathname)

        if (seg[0] === 'auth' && seg[1] === 'login' && method === 'POST') {
            const body = request.postDataJSON() as { username?: string; password?: string }
            const user = body.username ? USERS[body.username] : undefined
            if (!user || body.password !== 'password') {
                return json(route, { code: 'UNAUTHORIZED', message: 'Invalid username or password' }, 401)
            }
            return json(route, {
                accessToken: `mock-token-${user.username}`,
                tokenType: 'Bearer',
                expiresIn: 3600,
                user,
            })
        }

        if (seg[0] === 'requests' && seg.length === 1 && method === 'GET') {
            return json(route, buildRequestPage())
        }

        if (seg[0] === 'requests' && seg.length === 1 && method === 'POST') {
            const user = getActiveUserFromAuthHeader(await request.headerValue('authorization'))
            const body = request.postDataJSON() as { title: string; description: string }
            const id = seedRequest({ title: body.title, description: body.description, owner: user })
            return json(route, requests.get(id), 201)
        }

        if (seg[0] === 'requests' && seg.length === 2 && method === 'GET') {
            const requestId = Number(seg[1])
            const req = requests.get(requestId)
            if (!req) return json(route, { code: 'NOT_FOUND', message: 'SupportRequest not found' }, 404)
            return json(route, req)
        }

        if (seg[0] === 'requests' && seg.length === 2 && method === 'DELETE') {
            const requestId = Number(seg[1])
            requests.delete(requestId)
            commentsByRequest.delete(requestId)
            attachmentsByRequest.delete(requestId)
            for (const [commentId] of attachmentsByComment.entries()) {
                const found = Array.from(commentsByRequest.values()).flat().find((c) => c.id === commentId)
                if (!found) attachmentsByComment.delete(commentId)
            }
            return route.fulfill({ status: 204 })
        }

        if (seg[0] === 'requests' && seg[2] === 'comments' && seg.length === 3 && method === 'GET') {
            const requestId = Number(seg[1])
            if (!requests.has(requestId)) {
                return json(route, { code: 'NOT_FOUND', message: 'SupportRequest not found' }, 404)
            }
            return json(route, buildCommentPage(requestId))
        }

        if (seg[0] === 'requests' && seg[2] === 'comments' && seg.length === 3 && method === 'POST') {
            const requestId = Number(seg[1])
            const req = requests.get(requestId)
            if (!req) return json(route, { code: 'NOT_FOUND', message: 'SupportRequest not found' }, 404)
            const user = getActiveUserFromAuthHeader(await request.headerValue('authorization'))
            const body = request.postDataJSON() as { body: string }
            const comment: MockComment = {
                id: nextCommentId++,
                requestId,
                author: user,
                body: body.body,
                createdAt: nowIso(),
            }
            const arr = commentsByRequest.get(requestId) ?? []
            arr.push(comment)
            commentsByRequest.set(requestId, arr)
            req.updatedAt = nowIso()
            return json(route, comment, 201)
        }

        if (seg[0] === 'requests' && seg[2] === 'comments' && seg.length === 4 && method === 'DELETE') {
            const requestId = Number(seg[1])
            const commentId = Number(seg[3])
            const arr = commentsByRequest.get(requestId) ?? []
            commentsByRequest.set(requestId, arr.filter((c) => c.id !== commentId))
            attachmentsByComment.delete(commentId)
            return route.fulfill({ status: 204 })
        }

        if (seg[0] === 'requests' && seg[2] === 'attachments' && seg.length === 4 && seg[3] === 'upload-url' && method === 'POST') {
            const requestId = Number(seg[1])
            const req = requests.get(requestId)
            if (!req) return json(route, { code: 'NOT_FOUND', message: 'SupportRequest not found' }, 404)
            const body = request.postDataJSON() as { fileName: string; contentType: string; fileSize: number }
            const attachment: MockAttachment = {
                id: nextAttachmentId++,
                requestId,
                commentId: null,
                fileName: body.fileName,
                contentType: body.contentType,
                fileSize: body.fileSize,
                state: 'PENDING',
                uploadedBy: req.createdBy,
                createdAt: nowIso(),
            }
            const arr = attachmentsByRequest.get(requestId) ?? []
            arr.push(attachment)
            attachmentsByRequest.set(requestId, arr)
            return json(route, {
                attachmentId: attachment.id,
                uploadUrl: `https://mock-s3.local/upload/${attachment.id}`,
                expiresAt: nowIso(),
            }, 201)
        }

        if (seg[0] === 'requests' && seg[2] === 'attachments' && seg.length === 5 && seg[4] === 'confirm' && method === 'POST') {
            const requestId = Number(seg[1])
            const attachmentId = Number(seg[3])
            const arr = attachmentsByRequest.get(requestId) ?? []
            const attachment = arr.find((a) => a.id === attachmentId)
            if (!attachment) return json(route, { code: 'NOT_FOUND', message: 'Attachment not found' }, 404)
            attachment.state = 'ACTIVE'
            return json(route, attachment)
        }

        if (seg[0] === 'requests' && seg[2] === 'attachments' && seg.length === 3 && method === 'GET') {
            const requestId = Number(seg[1])
            return json(route, attachmentsByRequest.get(requestId) ?? [])
        }

        if (seg[0] === 'requests' && seg[2] === 'attachments' && seg.length === 5 && seg[4] === 'download-url' && method === 'GET') {
            const requestId = Number(seg[1])
            const attachmentId = Number(seg[3])
            const arr = attachmentsByRequest.get(requestId) ?? []
            const attachment = arr.find((a) => a.id === attachmentId)
            if (!attachment) return json(route, { code: 'NOT_FOUND', message: 'Attachment not found' }, 404)
            return json(route, {
                attachmentId,
                downloadUrl: `https://mock-s3.local/download/${attachmentId}`,
                expiresAt: nowIso(),
            })
        }

        if (seg[0] === 'requests' && seg[2] === 'attachments' && seg.length === 4 && method === 'DELETE') {
            const requestId = Number(seg[1])
            const attachmentId = Number(seg[3])
            const arr = attachmentsByRequest.get(requestId) ?? []
            attachmentsByRequest.set(requestId, arr.filter((a) => a.id !== attachmentId))
            return route.fulfill({ status: 204 })
        }

        if (seg[0] === 'requests' && seg[2] === 'comments' && seg[4] === 'attachments' && seg.length === 6 && seg[5] === 'upload-url' && method === 'POST') {
            const requestId = Number(seg[1])
            const commentId = Number(seg[3])
            const req = requests.get(requestId)
            if (!req) return json(route, { code: 'NOT_FOUND', message: 'SupportRequest not found' }, 404)
            const body = request.postDataJSON() as { fileName: string; contentType: string; fileSize: number }
            const attachment: MockAttachment = {
                id: nextAttachmentId++,
                requestId: null,
                commentId,
                fileName: body.fileName,
                contentType: body.contentType,
                fileSize: body.fileSize,
                state: 'PENDING',
                uploadedBy: req.createdBy,
                createdAt: nowIso(),
            }
            const arr = attachmentsByComment.get(commentId) ?? []
            arr.push(attachment)
            attachmentsByComment.set(commentId, arr)
            return json(route, {
                attachmentId: attachment.id,
                uploadUrl: `https://mock-s3.local/upload/${attachment.id}`,
                expiresAt: nowIso(),
            }, 201)
        }

        if (seg[0] === 'requests' && seg[2] === 'comments' && seg[4] === 'attachments' && seg.length === 7 && seg[6] === 'confirm' && method === 'POST') {
            const commentId = Number(seg[3])
            const attachmentId = Number(seg[5])
            const arr = attachmentsByComment.get(commentId) ?? []
            const attachment = arr.find((a) => a.id === attachmentId)
            if (!attachment) return json(route, { code: 'NOT_FOUND', message: 'Attachment not found' }, 404)
            attachment.state = 'ACTIVE'
            return json(route, attachment)
        }

        if (seg[0] === 'requests' && seg[2] === 'comments' && seg[4] === 'attachments' && seg.length === 5 && method === 'GET') {
            const commentId = Number(seg[3])
            return json(route, attachmentsByComment.get(commentId) ?? [])
        }

        if (seg[0] === 'requests' && seg[2] === 'comments' && seg[4] === 'attachments' && seg.length === 7 && seg[6] === 'download-url' && method === 'GET') {
            const commentId = Number(seg[3])
            const attachmentId = Number(seg[5])
            const arr = attachmentsByComment.get(commentId) ?? []
            const attachment = arr.find((a) => a.id === attachmentId)
            if (!attachment) return json(route, { code: 'NOT_FOUND', message: 'Attachment not found' }, 404)
            return json(route, {
                attachmentId,
                downloadUrl: `https://mock-s3.local/download/${attachmentId}`,
                expiresAt: nowIso(),
            })
        }

        if (seg[0] === 'requests' && seg[2] === 'comments' && seg[4] === 'attachments' && seg.length === 6 && method === 'DELETE') {
            const commentId = Number(seg[3])
            const attachmentId = Number(seg[5])
            const arr = attachmentsByComment.get(commentId) ?? []
            attachmentsByComment.set(commentId, arr.filter((a) => a.id !== attachmentId))
            return route.fulfill({ status: 204 })
        }

        return json(route, { code: 'NOT_FOUND', message: `No mock route for ${method} ${url.pathname}` }, 404)
    })

    await page.route('https://mock-s3.local/**', async (route) => {
        const request = route.request()
        const method = request.method()
        if (method === 'PUT') {
            if (failNextPresignedPutStatus != null) {
                const status = failNextPresignedPutStatus
                failNextPresignedPutStatus = null
                return route.fulfill({ status, body: '' })
            }
            return route.fulfill({ status: 200, body: '' })
        }
        if (method === 'GET') {
            return route.fulfill({ status: 200, body: 'mock-file' })
        }
        return route.fulfill({ status: 200, body: '' })
    })

    return {
        seedRequest,
        seedRequestAttachments,
        setNextPresignedPutFailureStatus: (status: number | null) => {
            failNextPresignedPutStatus = status
        },
    }
}
