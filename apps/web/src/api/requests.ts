import apiClient from './client'

export type RequestStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'

export interface UserSummary {
    id: number
    username: string
    email: string
    roles: string[]
}

export interface SupportRequest {
    id: number
    title: string
    description: string
    status: RequestStatus
    createdBy: UserSummary
    assignedTo: UserSummary | null
    createdAt: string
    updatedAt: string
    commentCount: number
}

export interface SupportRequestPage {
    content: SupportRequest[]
    totalElements: number
    totalPages: number
    number: number
    size: number
}

export interface CreateRequestPayload {
    title: string
    description: string
}

export interface UpdateRequestPayload {
    status?: RequestStatus
    assignedToId?: number
}

export interface Comment {
    id: number
    requestId: number
    author: UserSummary
    body: string
    createdAt: string
}

export interface CommentPage {
    content: Comment[]
    totalElements: number
    totalPages: number
    number: number
}

// Requests
export const fetchRequests = async (
    page = 0,
    size = 20,
    status?: RequestStatus
): Promise<SupportRequestPage> => {
    const params: Record<string, unknown> = { page, size, sort: 'createdAt', direction: 'desc' }
    if (status) params.status = status
    const { data } = await apiClient.get<SupportRequestPage>('/requests', { params })
    return data
}

export const fetchRequest = async (id: number): Promise<SupportRequest> => {
    const { data } = await apiClient.get<SupportRequest>(`/requests/${id}`)
    return data
}

export const createRequest = async (payload: CreateRequestPayload): Promise<SupportRequest> => {
    const { data } = await apiClient.post<SupportRequest>('/requests', payload)
    return data
}

export const updateRequest = async (
    id: number,
    payload: UpdateRequestPayload
): Promise<SupportRequest> => {
    const { data } = await apiClient.patch<SupportRequest>(`/requests/${id}`, payload)
    return data
}

// Comments
export const fetchComments = async (requestId: number, page = 0): Promise<CommentPage> => {
    const { data } = await apiClient.get<CommentPage>(`/requests/${requestId}/comments`, {
        params: { page, size: 50 },
    })
    return data
}

export const addComment = async (requestId: number, body: string): Promise<Comment> => {
    const { data } = await apiClient.post<Comment>(`/requests/${requestId}/comments`, { body })
    return data
}
