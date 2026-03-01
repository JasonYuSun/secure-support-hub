import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { fetchRequests, updateRequest, type RequestStatus } from '../api/requests'
import StatusBadge from '../components/StatusBadge'
import AssigneeSelect from '../components/AssigneeSelect'
import { ChevronLeft, ChevronRight } from 'lucide-react'

const STATUS_TRANSITIONS: Record<RequestStatus, RequestStatus[]> = {
    OPEN: ['IN_PROGRESS', 'CLOSED'],
    IN_PROGRESS: ['RESOLVED', 'CLOSED'],
    RESOLVED: ['CLOSED'],
    CLOSED: [],
}

const TriagePage: React.FC = () => {
    const navigate = useNavigate()
    const queryClient = useQueryClient()
    const [page, setPage] = useState(0)
    const [statusFilter, setStatusFilter] = useState<RequestStatus | ''>('')

    const { data, isLoading, error } = useQuery({
        queryKey: ['triage-requests', page, statusFilter],
        queryFn: () => fetchRequests(page, 20, statusFilter || undefined),
    })

    const mutation = useMutation({
        mutationFn: ({ id, status }: { id: number; status: RequestStatus }) =>
            updateRequest(id, { status }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['triage-requests'] })
            queryClient.invalidateQueries({ queryKey: ['requests'] })
        },
    })

    const formatDate = (iso: string) =>
        new Date(iso).toLocaleDateString('en-AU', { day: 'numeric', month: 'short', year: 'numeric' })

    return (
        <div className="page-wrapper">
            <div className="container">
                <div className="page-header">
                    <div>
                        <h1 className="page-title">Triage Queue</h1>
                        <p className="page-subtitle">Manage and update all support requests</p>
                    </div>
                    <div className="flex gap-2">
                        {(['', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'] as const).map(s => (
                            <button
                                key={s}
                                className={`btn ${statusFilter === s ? 'btn-primary' : 'btn-secondary'}`}
                                style={{ padding: '5px 12px', fontSize: 12 }}
                                onClick={() => { setStatusFilter(s); setPage(0) }}
                            >
                                {s || 'All'}
                            </button>
                        ))}
                    </div>
                </div>

                {isLoading && <div className="loading-center"><div className="spinner" /></div>}
                {error && <div className="alert alert-error">Failed to load requests.</div>}

                {data && (
                    <>
                        {data.content.length === 0 ? (
                            <div className="empty-state"><h3>No requests found</h3></div>
                        ) : (
                            <div className="table-wrap">
                                <table className="table">
                                    <thead>
                                        <tr>
                                            <th>#</th>
                                            <th>Title</th>
                                            <th>Status</th>
                                            <th>Created by</th>
                                            <th>Assigned to</th>
                                            <th>Created</th>
                                            <th>Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {data.content.map(req => (
                                            <tr key={req.id}>
                                                <td className="text-muted text-sm">{req.id}</td>
                                                <td>
                                                    <span
                                                        style={{ fontWeight: 500, cursor: 'pointer', color: 'var(--color-primary-h)' }}
                                                        onClick={() => navigate(`/requests/${req.id}`)}
                                                    >
                                                        {req.title}
                                                    </span>
                                                </td>
                                                <td><StatusBadge status={req.status} /></td>
                                                <td className="text-sm text-muted">{req.createdBy.username}</td>
                                                <td className="text-sm">
                                                    <AssigneeSelect
                                                        requestId={req.id}
                                                        currentAssigneeId={req.assignedTo?.id}
                                                    />
                                                </td>
                                                <td className="text-sm text-muted">{formatDate(req.createdAt)}</td>
                                                <td>
                                                    <div className="flex gap-2">
                                                        {STATUS_TRANSITIONS[req.status].map(next => (
                                                            <button
                                                                key={next}
                                                                className="btn btn-secondary"
                                                                style={{ padding: '3px 10px', fontSize: 11 }}
                                                                disabled={mutation.isPending}
                                                                onClick={() => mutation.mutate({ id: req.id, status: next })}
                                                            >
                                                                → {next.replace('_', ' ')}
                                                            </button>
                                                        ))}
                                                    </div>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}

                        {data.totalPages > 1 && (
                            <div className="pagination">
                                <button className="page-btn" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
                                    <ChevronLeft size={14} />
                                </button>
                                <span className="page-info">Page {page + 1} of {data.totalPages}</span>
                                <button className="page-btn" disabled={page >= data.totalPages - 1} onClick={() => setPage(p => p + 1)}>
                                    <ChevronRight size={14} />
                                </button>
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    )
}

export default TriagePage
