import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { fetchRequests, type RequestStatus } from '../api/requests'
import StatusBadge from '../components/StatusBadge'
import { PlusCircle, ChevronLeft, ChevronRight, Filter } from 'lucide-react'

const STATUS_OPTIONS: { label: string; value: RequestStatus | '' }[] = [
    { label: 'All', value: '' },
    { label: 'Open', value: 'OPEN' },
    { label: 'In Progress', value: 'IN_PROGRESS' },
    { label: 'Resolved', value: 'RESOLVED' },
    { label: 'Closed', value: 'CLOSED' },
]

const DashboardPage: React.FC = () => {
    const navigate = useNavigate()
    const [page, setPage] = useState(0)
    const [statusFilter, setStatusFilter] = useState<RequestStatus | ''>('')

    const { data, isLoading, error } = useQuery({
        queryKey: ['requests', page, statusFilter],
        queryFn: () => fetchRequests(page, 20, statusFilter || undefined),
    })

    const formatDate = (iso: string) =>
        new Date(iso).toLocaleDateString('en-AU', { day: 'numeric', month: 'short', year: 'numeric' })

    return (
        <div className="page-wrapper">
            <div className="container">
                <div className="page-header">
                    <div>
                        <h1 className="page-title">My Requests</h1>
                        <p className="page-subtitle">View and manage your support requests</p>
                    </div>
                    <button className="btn btn-primary" onClick={() => navigate('/requests/new')}>
                        <PlusCircle size={16} />
                        New Request
                    </button>
                </div>

                {/* Filters */}
                <div className="flex items-center gap-3 mb-6">
                    <Filter size={14} color="var(--color-text-muted)" />
                    <div className="flex gap-2">
                        {STATUS_OPTIONS.map(opt => (
                            <button
                                key={opt.value}
                                className={`btn ${statusFilter === opt.value ? 'btn-primary' : 'btn-secondary'}`}
                                style={{ padding: '5px 14px', fontSize: 12 }}
                                onClick={() => { setStatusFilter(opt.value as RequestStatus | ''); setPage(0) }}
                            >
                                {opt.label}
                            </button>
                        ))}
                    </div>
                </div>

                {isLoading && <div className="loading-center"><div className="spinner" /></div>}
                {error && <div className="alert alert-error">Failed to load requests.</div>}

                {data && (
                    <>
                        {data.content.length === 0 ? (
                            <div className="empty-state">
                                <h3>No requests yet</h3>
                                <p>Create your first support request to get started.</p>
                            </div>
                        ) : (
                            <div className="table-wrap">
                                <table className="table">
                                    <thead>
                                        <tr>
                                            <th>#</th>
                                            <th>Title</th>
                                            <th>Status</th>
                                            <th>Assigned To</th>
                                            <th>Comments</th>
                                            <th>Created</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {data.content.map(req => (
                                            <tr key={req.id} onClick={() => navigate(`/requests/${req.id}`)}>
                                                <td className="text-muted text-sm">{req.id}</td>
                                                <td style={{ fontWeight: 500 }}>{req.title}</td>
                                                <td><StatusBadge status={req.status} /></td>
                                                <td className="text-sm text-muted">
                                                    {req.assignedTo?.username ?? <span style={{ color: 'var(--color-text-subtle)' }}>—</span>}
                                                </td>
                                                <td className="text-sm text-muted">{req.commentCount}</td>
                                                <td className="text-sm text-muted">{formatDate(req.createdAt)}</td>
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

export default DashboardPage
