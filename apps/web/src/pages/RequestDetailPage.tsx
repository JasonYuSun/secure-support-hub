import React, { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchRequest, fetchComments, addComment } from '../api/requests'
import StatusBadge from '../components/StatusBadge'
import { ArrowLeft, MessageSquare, Send } from 'lucide-react'

const RequestDetailPage: React.FC = () => {
    const { id } = useParams<{ id: string }>()
    const reqId = Number(id)
    const navigate = useNavigate()
    const queryClient = useQueryClient()
    const [commentBody, setCommentBody] = useState('')
    const [commentError, setCommentError] = useState('')

    const { data: request, isLoading, error } = useQuery({
        queryKey: ['request', reqId],
        queryFn: () => fetchRequest(reqId),
    })

    const { data: comments } = useQuery({
        queryKey: ['comments', reqId],
        queryFn: () => fetchComments(reqId),
        enabled: !!request,
    })

    const commentMutation = useMutation({
        mutationFn: () => addComment(reqId, commentBody),
        onSuccess: () => {
            setCommentBody('')
            queryClient.invalidateQueries({ queryKey: ['comments', reqId] })
            queryClient.invalidateQueries({ queryKey: ['request', reqId] })
        },
        onError: (err: unknown) => {
            const msg = (err as { response?: { data?: { message?: string } } })
                ?.response?.data?.message || 'Failed to add comment'
            setCommentError(msg)
        },
    })

    const handleAddComment = (e: React.FormEvent) => {
        e.preventDefault()
        setCommentError('')
        if (!commentBody.trim()) return
        commentMutation.mutate()
    }

    const formatDate = (iso: string) =>
        new Date(iso).toLocaleString('en-AU', {
            dateStyle: 'medium', timeStyle: 'short',
        })

    if (isLoading) return <div className="loading-center"><div className="spinner" /></div>
    if (error || !request) return (
        <div className="page-wrapper container">
            <div className="alert alert-error">Request not found or you don't have access.</div>
        </div>
    )

    return (
        <div className="page-wrapper">
            <div className="container" style={{ maxWidth: 780 }}>
                <button className="btn btn-secondary" style={{ marginBottom: 24, fontSize: 13, padding: '6px 14px' }}
                    onClick={() => navigate(-1)}>
                    <ArrowLeft size={14} /> Back
                </button>

                {/* Request card */}
                <div className="card" style={{ marginBottom: 24 }}>
                    <div className="flex items-center justify-between mb-6">
                        <div className="flex items-center gap-3">
                            <span className="text-muted text-sm">#{request.id}</span>
                            <StatusBadge status={request.status} />
                        </div>
                        <span className="text-xs text-muted">{formatDate(request.createdAt)}</span>
                    </div>

                    <h2 style={{ fontSize: '1.3rem', fontWeight: 700, marginBottom: 12 }}>{request.title}</h2>
                    <p style={{ color: 'var(--color-text-muted)', lineHeight: 1.8, whiteSpace: 'pre-wrap' }}>
                        {request.description}
                    </p>

                    <div style={{
                        marginTop: 24, paddingTop: 20, borderTop: '1px solid var(--color-border)',
                        display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16,
                    }}>
                        <div>
                            <p className="text-xs text-muted" style={{ marginBottom: 4 }}>Created by</p>
                            <p style={{ fontWeight: 500, fontSize: 14 }}>{request.createdBy.username}</p>
                        </div>
                        <div>
                            <p className="text-xs text-muted" style={{ marginBottom: 4 }}>Assigned to</p>
                            <p style={{ fontWeight: 500, fontSize: 14 }}>
                                {request.assignedTo?.username ?? <span style={{ color: 'var(--color-text-subtle)' }}>Unassigned</span>}
                            </p>
                        </div>
                        <div>
                            <p className="text-xs text-muted" style={{ marginBottom: 4 }}>Last updated</p>
                            <p style={{ fontWeight: 500, fontSize: 14 }}>{formatDate(request.updatedAt)}</p>
                        </div>
                    </div>
                </div>

                {/* Comments */}
                <div className="card">
                    <div className="flex items-center gap-2" style={{ marginBottom: 20 }}>
                        <MessageSquare size={16} color="var(--color-primary)" />
                        <h3>Comments ({comments?.totalElements ?? 0})</h3>
                    </div>

                    {comments && comments.content.length === 0 && (
                        <p className="text-muted text-sm" style={{ marginBottom: 20 }}>No comments yet. Be the first to comment.</p>
                    )}

                    <div>
                        {comments?.content.map(c => (
                            <div key={c.id} className="comment">
                                <div className="comment-meta">
                                    <span className="comment-author">{c.author.username}</span>
                                    {c.author.roles.map(r => (
                                        <span key={r} className={`role-badge role-${r}`}>{r}</span>
                                    ))}
                                    <span className="comment-time">{formatDate(c.createdAt)}</span>
                                </div>
                                <p className="comment-body">{c.body}</p>
                            </div>
                        ))}
                    </div>

                    {/* Add comment form */}
                    <form onSubmit={handleAddComment} style={{ marginTop: 24, display: 'flex', flexDirection: 'column', gap: 12 }}>
                        {commentError && <div className="alert alert-error">{commentError}</div>}
                        <textarea
                            className="form-textarea"
                            placeholder="Add a comment…"
                            value={commentBody}
                            onChange={e => setCommentBody(e.target.value)}
                            rows={3}
                            disabled={commentMutation.isPending}
                        />
                        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                            <button type="submit" className="btn btn-primary" disabled={commentMutation.isPending || !commentBody.trim()}>
                                {commentMutation.isPending
                                    ? <><span className="spinner" style={{ width: 14, height: 14, borderWidth: 2 }} /> Posting…</>
                                    : <><Send size={14} /> Post Comment</>}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    )
}

export default RequestDetailPage
