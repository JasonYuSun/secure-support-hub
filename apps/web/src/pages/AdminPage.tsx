import React from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import apiClient from '../api/client'

interface UserAdminDto {
    id: number
    username: string
    email: string
    roles: string[]
}

const AdminPage: React.FC = () => {
    const queryClient = useQueryClient()
    const { data: users, isLoading } = useQuery({
        queryKey: ['admin-users'],
        queryFn: async () => {
            const { data } = await apiClient.get<UserAdminDto[]>('/admin/users')
            return data
        }
    })

    const { data: roles } = useQuery({
        queryKey: ['admin-roles'],
        queryFn: async () => {
            const { data } = await apiClient.get<string[]>('/admin/roles')
            return data
        }
    })

    const updateRoleMutation = useMutation({
        mutationFn: async ({ userId, newRole }: { userId: number, newRole: string }) => {
            await apiClient.patch(`/admin/users/${userId}/roles`, { roles: [newRole] })
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['admin-users'] })
        }
    })

    if (isLoading) return <div className="page-wrapper"><div className="loading-center"><div className="spinner"></div></div></div>

    return (
        <div className="page-wrapper">
            <div className="container">
                <div className="page-header">
                    <div>
                        <h1 className="page-title">Admin Dashboard</h1>
                        <p className="page-subtitle">Manage system users and access roles</p>
                    </div>
                </div>

                <div className="card" style={{ padding: '24px' }}>
                    <h3 style={{ marginBottom: '16px', fontSize: '18px', fontWeight: 600 }}>User Management</h3>
                    <div className="table-wrap">
                        <table className="table">
                            <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>Username</th>
                                    <th>Email</th>
                                    <th>Current Role</th>
                                    <th>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {users?.map(user => (
                                    <tr key={user.id}>
                                        <td className="text-muted text-sm">{user.id}</td>
                                        <td style={{ fontWeight: 500 }}>{user.username}</td>
                                        <td className="text-muted text-sm">{user.email}</td>
                                        <td>
                                            <span style={{
                                                padding: '4px 8px',
                                                borderRadius: '4px',
                                                fontSize: '11px',
                                                fontWeight: 600,
                                                backgroundColor: 'var(--color-bg-elevated)',
                                                border: '1px solid var(--color-border)'
                                            }}>
                                                {user.roles.join(', ')}
                                            </span>
                                        </td>
                                        <td>
                                            <select
                                                className="input"
                                                style={{ padding: '4px 8px', fontSize: '12px', height: 'auto' }}
                                                value={user.roles[0] || 'USER'}
                                                onChange={(e) => updateRoleMutation.mutate({ userId: user.id, newRole: e.target.value })}
                                                disabled={updateRoleMutation.isPending || user.username === 'admin'}
                                            >
                                                {roles?.map(r => (
                                                    <option key={r} value={r}>Promote to {r}</option>
                                                ))}
                                            </select>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    )
}

export default AdminPage
