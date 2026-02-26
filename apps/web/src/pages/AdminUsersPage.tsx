import React, { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Save, Users } from 'lucide-react'
import {
    fetchAdminUsers,
    fetchAvailableRoles,
    updateAdminUserRoles,
    type AdminUser,
} from '../api/admin'
import { useAuth } from '../auth/AuthContext'

interface UpdateRolesVars {
    userId: number
    roles: string[]
}

const areSameRoles = (a: string[], b: string[]) => {
    if (a.length !== b.length) return false
    const as = [...new Set(a)].sort()
    const bs = [...new Set(b)].sort()
    return as.every((role, idx) => role === bs[idx])
}

const AdminUsersPage: React.FC = () => {
    const { user: currentUser } = useAuth()
    const queryClient = useQueryClient()
    const [draftRoles, setDraftRoles] = useState<Record<number, string[]>>({})
    const [rowErrors, setRowErrors] = useState<Record<number, string>>({})
    const [rowSuccess, setRowSuccess] = useState<Record<number, string>>({})

    const { data: users, isLoading: loadingUsers, error: usersError } = useQuery({
        queryKey: ['admin-users'],
        queryFn: fetchAdminUsers,
    })

    const { data: availableRoles, isLoading: loadingRoles, error: rolesError } = useQuery({
        queryKey: ['admin-roles'],
        queryFn: fetchAvailableRoles,
    })

    const roles = useMemo(
        () => (availableRoles && availableRoles.length > 0 ? availableRoles : ['USER', 'TRIAGE', 'ADMIN']),
        [availableRoles]
    )

    const mutation = useMutation({
        mutationFn: ({ userId, roles }: UpdateRolesVars) =>
            updateAdminUserRoles(userId, { roles }),
        onSuccess: (_, vars) => {
            setRowErrors(prev => ({ ...prev, [vars.userId]: '' }))
            setRowSuccess(prev => ({ ...prev, [vars.userId]: 'Roles updated' }))
            setDraftRoles(prev => {
                const next = { ...prev }
                delete next[vars.userId]
                return next
            })
            queryClient.invalidateQueries({ queryKey: ['admin-users'] })
        },
        onError: (err: unknown, vars) => {
            const message = (err as { response?: { data?: { message?: string } } })
                ?.response?.data?.message || 'Failed to update roles'
            setRowErrors(prev => ({ ...prev, [vars.userId]: message }))
            setRowSuccess(prev => ({ ...prev, [vars.userId]: '' }))
        },
    })

    const roleSelection = (user: AdminUser) => draftRoles[user.id] ?? user.roles

    const toggleRole = (user: AdminUser, role: string) => {
        const selected = roleSelection(user)
        const hasRole = selected.includes(role)
        const isCurrentUser = currentUser?.username === user.username

        if (hasRole && selected.length === 1) {
            setRowErrors(prev => ({ ...prev, [user.id]: 'A user must have at least one role' }))
            return
        }

        if (isCurrentUser && role === 'ADMIN' && hasRole) {
            setRowErrors(prev => ({ ...prev, [user.id]: 'You cannot remove your own ADMIN role' }))
            return
        }

        const nextRoles = hasRole
            ? selected.filter(item => item !== role)
            : [...selected, role]

        setDraftRoles(prev => ({ ...prev, [user.id]: nextRoles }))
        setRowErrors(prev => ({ ...prev, [user.id]: '' }))
        setRowSuccess(prev => ({ ...prev, [user.id]: '' }))
    }

    const saveRoles = (user: AdminUser) => {
        const selectedRoles = roleSelection(user)
        if (selectedRoles.length === 0) {
            setRowErrors(prev => ({ ...prev, [user.id]: 'At least one role is required' }))
            return
        }

        mutation.mutate({ userId: user.id, roles: selectedRoles })
    }

    return (
        <div className="page-wrapper">
            <div className="container">
                <div className="page-header">
                    <div>
                        <h1 className="page-title">User &amp; Role Management</h1>
                        <p className="page-subtitle">Admin-only access to view users and update role assignments</p>
                    </div>
                </div>

                <div className="card" style={{ marginBottom: 24 }}>
                    <div className="flex items-center gap-2">
                        <Users size={16} color="var(--color-primary-h)" />
                        <p className="text-sm text-muted">
                            Toggle roles, then click save per user. Every user must keep at least one role.
                        </p>
                    </div>
                </div>

                {(loadingUsers || loadingRoles) && <div className="loading-center"><div className="spinner" /></div>}
                {(usersError || rolesError) && (
                    <div className="alert alert-error">Failed to load admin user management data.</div>
                )}

                {users && users.length > 0 && (
                    <div className="table-wrap">
                        <table className="table">
                            <thead>
                                <tr>
                                    <th>User</th>
                                    <th>Email</th>
                                    <th>Roles</th>
                                    <th>Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                {users.map(user => {
                                    const selectedRoles = roleSelection(user)
                                    const isDirty = !areSameRoles(selectedRoles, user.roles)
                                    const isSaving = mutation.isPending && mutation.variables?.userId === user.id

                                    return (
                                        <tr key={user.id}>
                                            <td>
                                                <div className="flex items-center gap-2">
                                                    <span style={{ fontWeight: 600 }}>{user.username}</span>
                                                    <span className="text-xs text-muted">#{user.id}</span>
                                                </div>
                                            </td>
                                            <td className="text-sm text-muted">{user.email}</td>
                                            <td>
                                                <div className="flex gap-2" style={{ flexWrap: 'wrap' }}>
                                                    {roles.map(role => {
                                                        const selected = selectedRoles.includes(role)
                                                        return (
                                                            <button
                                                                key={role}
                                                                type="button"
                                                                className={`btn ${selected ? 'btn-primary' : 'btn-secondary'}`}
                                                                style={{ padding: '4px 10px', fontSize: 11 }}
                                                                onClick={() => toggleRole(user, role)}
                                                                disabled={isSaving}
                                                            >
                                                                {role}
                                                            </button>
                                                        )
                                                    })}
                                                </div>
                                                {rowErrors[user.id] && (
                                                    <p className="form-error mt-2">{rowErrors[user.id]}</p>
                                                )}
                                                {rowSuccess[user.id] && (
                                                    <p className="text-xs mt-2" style={{ color: 'var(--color-success)' }}>
                                                        {rowSuccess[user.id]}
                                                    </p>
                                                )}
                                            </td>
                                            <td>
                                                <button
                                                    type="button"
                                                    className="btn btn-secondary"
                                                    style={{ padding: '6px 12px', fontSize: 12 }}
                                                    onClick={() => saveRoles(user)}
                                                    disabled={!isDirty || isSaving}
                                                >
                                                    {isSaving
                                                        ? <><span className="spinner" style={{ width: 13, height: 13, borderWidth: 2 }} /> Saving…</>
                                                        : <><Save size={13} /> Save</>}
                                                </button>
                                            </td>
                                        </tr>
                                    )
                                })}
                            </tbody>
                        </table>
                    </div>
                )}

                {users && users.length === 0 && (
                    <div className="empty-state">
                        <h3>No users found</h3>
                    </div>
                )}
            </div>
        </div>
    )
}

export default AdminUsersPage
