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

export type AttachmentState = 'PENDING' | 'ACTIVE' | 'FAILED'

export interface Attachment {
    id: number
    requestId: number | null
    commentId: number | null
    fileName: string
    contentType: string
    fileSize: number
    state: AttachmentState
    uploadedBy: UserSummary
    createdAt: string
}

export interface AttachmentUploadUrlRequest {
    fileName: string
    contentType: string
    fileSize: number
}

export interface AttachmentUploadUrlResponse {
    attachmentId: number
    uploadUrl: string
    expiresAt: string
}

export interface AttachmentDownloadUrlResponse {
    attachmentId: number
    downloadUrl: string
    expiresAt: string
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

export const deleteRequest = async (id: number): Promise<void> => {
    await apiClient.delete(`/requests/${id}`)
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

export const deleteComment = async (requestId: number, commentId: number): Promise<void> => {
    await apiClient.delete(`/requests/${requestId}/comments/${commentId}`)
}

// Request attachments
export const createRequestAttachmentUploadUrl = async (
    requestId: number,
    payload: AttachmentUploadUrlRequest
): Promise<AttachmentUploadUrlResponse> => {
    const { data } = await apiClient.post<AttachmentUploadUrlResponse>(
        `/requests/${requestId}/attachments/upload-url`,
        payload
    )
    return data
}

export const confirmRequestAttachment = async (
    requestId: number,
    attachmentId: number
): Promise<Attachment> => {
    const { data } = await apiClient.post<Attachment>(
        `/requests/${requestId}/attachments/${attachmentId}/confirm`
    )
    return data
}

export const fetchRequestAttachments = async (requestId: number): Promise<Attachment[]> => {
    const { data } = await apiClient.get<Attachment[]>(`/requests/${requestId}/attachments`)
    return data
}

export const fetchRequestAttachmentDownloadUrl = async (
    requestId: number,
    attachmentId: number
): Promise<AttachmentDownloadUrlResponse> => {
    const { data } = await apiClient.get<AttachmentDownloadUrlResponse>(
        `/requests/${requestId}/attachments/${attachmentId}/download-url`
    )
    return data
}

export const deleteRequestAttachment = async (
    requestId: number,
    attachmentId: number
): Promise<void> => {
    await apiClient.delete(`/requests/${requestId}/attachments/${attachmentId}`)
}

// Comment attachments
export const createCommentAttachmentUploadUrl = async (
    requestId: number,
    commentId: number,
    payload: AttachmentUploadUrlRequest
): Promise<AttachmentUploadUrlResponse> => {
    const { data } = await apiClient.post<AttachmentUploadUrlResponse>(
        `/requests/${requestId}/comments/${commentId}/attachments/upload-url`,
        payload
    )
    return data
}

export const confirmCommentAttachment = async (
    requestId: number,
    commentId: number,
    attachmentId: number
): Promise<Attachment> => {
    const { data } = await apiClient.post<Attachment>(
        `/requests/${requestId}/comments/${commentId}/attachments/${attachmentId}/confirm`
    )
    return data
}

export const fetchCommentAttachments = async (
    requestId: number,
    commentId: number
): Promise<Attachment[]> => {
    const { data } = await apiClient.get<Attachment[]>(
        `/requests/${requestId}/comments/${commentId}/attachments`
    )
    return data
}

export const fetchCommentAttachmentDownloadUrl = async (
    requestId: number,
    commentId: number,
    attachmentId: number
): Promise<AttachmentDownloadUrlResponse> => {
    const { data } = await apiClient.get<AttachmentDownloadUrlResponse>(
        `/requests/${requestId}/comments/${commentId}/attachments/${attachmentId}/download-url`
    )
    return data
}

export const deleteCommentAttachment = async (
    requestId: number,
    commentId: number,
    attachmentId: number
): Promise<void> => {
    await apiClient.delete(`/requests/${requestId}/comments/${commentId}/attachments/${attachmentId}`)
}
