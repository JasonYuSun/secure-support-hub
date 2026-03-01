import React from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchAssignableUsers } from '../api/users'
import { updateRequest } from '../api/requests'

interface AssigneeSelectProps {
    requestId: number
    currentAssigneeId?: number
}

const AssigneeSelect: React.FC<AssigneeSelectProps> = ({ requestId, currentAssigneeId }) => {
    const queryClient = useQueryClient()

    const { data: users, isLoading } = useQuery({
        queryKey: ['assignable-users'],
        queryFn: fetchAssignableUsers,
        staleTime: 5 * 60 * 1000, // 5 min cache
    })

    const mutation = useMutation({
        mutationFn: (newAssigneeId: number | null) =>
            updateRequest(requestId, { assignedToId: newAssigneeId === null ? undefined : newAssigneeId }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['triage-requests'] })
            queryClient.invalidateQueries({ queryKey: ['requests'] })
            queryClient.invalidateQueries({ queryKey: ['request', requestId] })
        },
    })

    if (isLoading) return <span className="text-sm text-muted">Loading...</span>

    return (
        <select
            className="input"
            style={{ padding: '4px 8px', fontSize: '13px', height: 'auto', minWidth: '120px' }}
            value={currentAssigneeId || ''}
            onChange={(e) => {
                const val = e.target.value
                mutation.mutate(val ? Number(val) : null)
            }}
            disabled={mutation.isPending}
        >
            <option value="">— Unassigned —</option>
            {users?.map(u => (
                <option key={u.id} value={u.id}>{u.username}</option>
            ))}
        </select>
    )
}

export default AssigneeSelect
