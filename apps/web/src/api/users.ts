import apiClient from './client'

export interface UserSummary {
    id: number
    username: string
    email: string
    roles: string[]
}

export const fetchAssignableUsers = async (): Promise<UserSummary[]> => {
    // Fetch users with TRIAGE role
    const triageRes = await apiClient.get<UserSummary[]>('/users?role=TRIAGE')
    // Fetch users with ADMIN role
    const adminRes = await apiClient.get<UserSummary[]>('/users?role=ADMIN')
    
    // Combine and deduplicate
    const combined = [...triageRes.data, ...adminRes.data]
    const uniqueIds = new Set<number>()
    return combined.filter(u => {
        if (uniqueIds.has(u.id)) return false
        uniqueIds.add(u.id)
        return true
    })
}
