import apiClient from './client'

export interface AdminUser {
    id: number
    username: string
    email: string
    roles: string[]
}

export interface UpdateUserRolesPayload {
    roles: string[]
}

export const fetchAdminUsers = async (): Promise<AdminUser[]> => {
    const { data } = await apiClient.get<AdminUser[]>('/admin/users')
    return data
}

export const fetchAvailableRoles = async (): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/admin/roles')
    return data
}

export const updateAdminUserRoles = async (
    userId: number,
    payload: UpdateUserRolesPayload
): Promise<AdminUser> => {
    const { data } = await apiClient.patch<AdminUser>(`/admin/users/${userId}/roles`, payload)
    return data
}
