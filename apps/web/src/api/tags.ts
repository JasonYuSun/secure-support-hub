import apiClient from './client'

export interface Tag {
    id: number
    name: string
    createdBy: {
        id: number
        username: string
        email: string
        roles: string[]
    }
    createdAt: string
}

// Tag dictionary
export const fetchTags = async (): Promise<Tag[]> => {
    const { data } = await apiClient.get<Tag[]>('/tags')
    return data
}

export const createTag = async (name: string): Promise<Tag> => {
    const { data } = await apiClient.post<Tag>('/tags', { name })
    return data
}

export const deleteTag = async (tagId: number): Promise<void> => {
    await apiClient.delete(`/tags/${tagId}`)
}

// Request tags
export const fetchRequestTags = async (requestId: number): Promise<Tag[]> => {
    const { data } = await apiClient.get<Tag[]>(`/requests/${requestId}/tags`)
    return data
}

export const applyRequestTag = async (requestId: number, tagId: number): Promise<Tag> => {
    const { data } = await apiClient.post<Tag>(`/requests/${requestId}/tags/${tagId}`)
    return data
}

export const unapplyRequestTag = async (requestId: number, tagId: number): Promise<void> => {
    await apiClient.delete(`/requests/${requestId}/tags/${tagId}`)
}

export const applyTag = async (requestId: number, tagName: string, allowCreate: boolean = false): Promise<void> => {
    const tags = await fetchTags()
    let tag = tags.find(t => t.name.toLowerCase() === tagName.toLowerCase())
    if (!tag) {
        if (!allowCreate) {
            throw new Error(`Tag '${tagName}' does not exist in the dictionary.`)
        }
        tag = await createTag(tagName)
    }
    const reqTags = await fetchRequestTags(requestId)
    if (!reqTags.find(t => t.id === tag!.id)) {
        await applyRequestTag(requestId, tag.id)
    }
}
