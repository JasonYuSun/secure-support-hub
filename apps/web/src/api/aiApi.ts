import apiClient from './client'

export interface AiActionRequestDto {
    promptOverride?: string
}

export interface AiSummarizeResponseDto {
    summary: string
    runId: string
    provider: string
    model: string
    latencyMs: number
    generatedAt: string
}

export interface TagSuggestion {
    existingTagId?: number
    name: string
    isNew: boolean
    reason: string
}

export interface AiSuggestTagsResponseDto {
    tags: TagSuggestion[]
    runId: string
    provider: string
    model: string
    latencyMs: number
    generatedAt: string
}

export interface AiDraftResponseDto {
    draft: string
    runId: string
    provider: string
    model: string
    latencyMs: number
    generatedAt: string
}

export async function summarizeRequest(requestId: number, data?: AiActionRequestDto): Promise<AiSummarizeResponseDto> {
    const res = await apiClient.post<AiSummarizeResponseDto>(`/requests/${requestId}/ai/summarize`, data || {})
    return res.data
}

export async function suggestTags(requestId: number, data?: AiActionRequestDto): Promise<AiSuggestTagsResponseDto> {
    const res = await apiClient.post<AiSuggestTagsResponseDto>(`/requests/${requestId}/ai/suggest-tags`, data || {})
    return res.data
}

export async function draftResponse(requestId: number, data?: AiActionRequestDto): Promise<AiDraftResponseDto> {
    const res = await apiClient.post<AiDraftResponseDto>(`/requests/${requestId}/ai/draft-response`, data || {})
    return res.data
}
