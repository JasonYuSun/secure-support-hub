import apiClient from './client'

export interface LoginPayload {
    username: string
    password: string
}

export interface AuthUser {
    id: number
    username: string
    email: string
    roles: string[]
}

export interface AuthResponse {
    accessToken: string
    tokenType: string
    expiresIn: number
    user: AuthUser
}

export const login = async (payload: LoginPayload): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>('/auth/login', payload)
    return data
}

