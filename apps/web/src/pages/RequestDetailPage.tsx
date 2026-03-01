import React, { useMemo, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import {
    addComment,
    confirmCommentAttachment,
    confirmRequestAttachment,
    createCommentAttachmentUploadUrl,
    createRequestAttachmentUploadUrl,
    deleteComment,
    deleteCommentAttachment,
    deleteRequest,
    deleteRequestAttachment,
    fetchCommentAttachmentDownloadUrl,
    fetchCommentAttachments,
    fetchComments,
    fetchRequest,
    fetchRequestAttachmentDownloadUrl,
    fetchRequestAttachments,
    updateRequest,
    type RequestStatus,
    type Attachment,
    type Comment,
} from '../api/requests'
import { useAuth } from '../auth/AuthContext'
import StatusBadge from '../components/StatusBadge'
import AttachmentList from '../components/AttachmentList'
import AssigneeSelect from '../components/AssigneeSelect'
import AttachmentUploader from '../components/AttachmentUploader'
import { ArrowLeft, MessageSquare, Send, Trash2 } from 'lucide-react'

const STATUS_TRANSITIONS: Record<RequestStatus, RequestStatus[]> = {
    OPEN: ['IN_PROGRESS', 'CLOSED'],
    IN_PROGRESS: ['RESOLVED', 'CLOSED'],
    RESOLVED: ['CLOSED'],
    CLOSED: [],
}

const ATTACHMENT_ALLOWED_MIME_TYPES = [
    'image/jpeg',
    'image/png',
    'image/webp',
    'application/pdf',
    'text/plain',
    'text/csv',
]
const ATTACHMENT_MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024
const REQUEST_ATTACHMENT_LIMIT = 10
const COMMENT_ATTACHMENT_LIMIT = 5

const extractErrorMessage = (err: unknown, fallback: string): string => {
    const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
    return message || fallback
}

const triggerBrowserDownload = (url: string) => {
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.target = '_blank'
    anchor.rel = 'noopener noreferrer'
    document.body.appendChild(anchor)
    anchor.click()
    document.body.removeChild(anchor)
}

const RequestDetailPage: React.FC = () => {
    const { id } = useParams<{ id: string }>()
    const reqId = Number(id)
    const isReqIdValid = Number.isFinite(reqId) && reqId > 0
    const navigate = useNavigate()
    const queryClient = useQueryClient()
    const { user, hasRole } = useAuth()

    const [commentBody, setCommentBody] = useState('')
    const [commentError, setCommentError] = useState('')
    const [actionError, setActionError] = useState('')
    const [deletingRequestAttachmentId, setDeletingRequestAttachmentId] = useState<number | null>(null)
    const [deletingCommentAttachmentKey, setDeletingCommentAttachmentKey] = useState<string | null>(null)
    const [deletingCommentId, setDeletingCommentId] = useState<number | null>(null)

    const { data: request, isLoading, error } = useQuery({
        queryKey: ['request', reqId],
        queryFn: () => fetchRequest(reqId),
        enabled: isReqIdValid,
    })

    const { data: comments } = useQuery({
        queryKey: ['comments', reqId],
        queryFn: () => fetchComments(reqId),
        enabled: Boolean(request),
    })

    const { data: requestAttachments = [] } = useQuery({
        queryKey: ['request-attachments', reqId],
        queryFn: () => fetchRequestAttachments(reqId),
        enabled: Boolean(request),
    })

    const commentAttachmentQueries = useQueries({
        queries: (comments?.content ?? []).map((comment) => ({
            queryKey: ['comment-attachments', reqId, comment.id],
            queryFn: () => fetchCommentAttachments(reqId, comment.id),
            enabled: Boolean(request),
        })),
    })

    const commentAttachmentsById = useMemo(() => {
        const map = new Map<number, Attachment[]>()
            ; (comments?.content ?? []).forEach((comment, index) => {
                map.set(comment.id, commentAttachmentQueries[index]?.data ?? [])
            })
        return map
    }, [comments?.content, commentAttachmentQueries])

    const isTriageOrAdmin = hasRole('TRIAGE') || hasRole('ADMIN')
    const isOwner = Boolean(request && user?.username === request.createdBy.username)
    const canManageRequestThread = isTriageOrAdmin || isOwner

    const commentMutation = useMutation({
        mutationFn: () => addComment(reqId, commentBody),
        onSuccess: () => {
            setCommentBody('')
            queryClient.invalidateQueries({ queryKey: ['comments', reqId] })
            queryClient.invalidateQueries({ queryKey: ['request', reqId] })
        },
        onError: (err: unknown) => {
            setCommentError(extractErrorMessage(err, 'Failed to add comment'))
        },
    })

    const requestDeleteMutation = useMutation({
        mutationFn: () => deleteRequest(reqId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['requests'] })
            queryClient.invalidateQueries({ queryKey: ['triage-requests'] })
            navigate('/')
        },
        onError: (err: unknown) => {
            setActionError(extractErrorMessage(err, 'Failed to delete request'))
        },
    })

    const commentDeleteMutation = useMutation({
        mutationFn: (commentId: number) => deleteComment(reqId, commentId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['comments', reqId] })
            queryClient.invalidateQueries({ queryKey: ['request', reqId] })
        },
        onError: (err: unknown) => {
            setActionError(extractErrorMessage(err, 'Failed to delete comment'))
        },
        onSettled: () => {
            setDeletingCommentId(null)
        },
    })

    const statusMutation = useMutation({
        mutationFn: (status: RequestStatus) => updateRequest(reqId, { status }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['request', reqId] })
            queryClient.invalidateQueries({ queryKey: ['triage-requests'] })
            queryClient.invalidateQueries({ queryKey: ['requests'] })
        },
        onError: (err: unknown) => {
            setActionError(extractErrorMessage(err, 'Failed to update status'))
        },
    })

    const requestAttachmentDeleteMutation = useMutation({
        mutationFn: (attachmentId: number) => deleteRequestAttachment(reqId, attachmentId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['request-attachments', reqId] })
        },
        onError: (err: unknown) => {
            setActionError(extractErrorMessage(err, 'Failed to delete request attachment'))
        },
        onSettled: () => {
            setDeletingRequestAttachmentId(null)
        },
    })

    const commentAttachmentDeleteMutation = useMutation({
        mutationFn: ({ commentId, attachmentId }: { commentId: number; attachmentId: number }) =>
            deleteCommentAttachment(reqId, commentId, attachmentId),
        onSuccess: (_data, variables) => {
            queryClient.invalidateQueries({ queryKey: ['comment-attachments', reqId, variables.commentId] })
        },
        onError: (err: unknown) => {
            setActionError(extractErrorMessage(err, 'Failed to delete comment attachment'))
        },
        onSettled: () => {
            setDeletingCommentAttachmentKey(null)
        },
    })

    const handleAddComment = (e: React.FormEvent) => {
        e.preventDefault()
        setCommentError('')
        if (!commentBody.trim()) return
        commentMutation.mutate()
    }

    const handleDeleteRequest = () => {
        if (!request) return
        setActionError('')
        if (!window.confirm(`Delete request #${request.id}? This also deletes comments and attachments.`)) return
        requestDeleteMutation.mutate()
    }

    const handleDeleteComment = (comment: Comment) => {
        setActionError('')
        if (!window.confirm('Delete this comment and its attachments?')) return
        setDeletingCommentId(comment.id)
        commentDeleteMutation.mutate(comment.id)
    }

    const handleRequestAttachmentDownload = async (attachment: Attachment) => {
        setActionError('')
        try {
            const response = await fetchRequestAttachmentDownloadUrl(reqId, attachment.id)
            triggerBrowserDownload(response.downloadUrl)
        } catch (err) {
            setActionError(extractErrorMessage(err, 'Failed to get download link for attachment'))
        }
    }

    const handleCommentAttachmentDownload = async (commentId: number, attachment: Attachment) => {
        setActionError('')
        try {
            const response = await fetchCommentAttachmentDownloadUrl(reqId, commentId, attachment.id)
            triggerBrowserDownload(response.downloadUrl)
        } catch (err) {
            setActionError(extractErrorMessage(err, 'Failed to get download link for attachment'))
        }
    }

    const handleDeleteRequestAttachment = (attachment: Attachment) => {
        setActionError('')
        if (!window.confirm(`Delete attachment "${attachment.fileName}"?`)) return
        setDeletingRequestAttachmentId(attachment.id)
        requestAttachmentDeleteMutation.mutate(attachment.id)
    }

    const handleDeleteCommentAttachment = (commentId: number, attachment: Attachment) => {
        setActionError('')
        if (!window.confirm(`Delete attachment "${attachment.fileName}"?`)) return
        setDeletingCommentAttachmentKey(`${commentId}-${attachment.id}`)
        commentAttachmentDeleteMutation.mutate({ commentId, attachmentId: attachment.id })
    }

    const formatDate = (iso: string) =>
        new Date(iso).toLocaleString('en-AU', {
            dateStyle: 'medium',
            timeStyle: 'short',
        })

    if (isLoading) return <div className="loading-center"><div className="spinner" /></div>
    if (error || !request) return (
        <div className="page-wrapper container">
            <div className="alert alert-error">Request not found or you don&apos;t have access.</div>
        </div>
    )

    return (
        <div className="page-wrapper">
            <div className="container" style={{ maxWidth: 920 }}>
                <button
                    className="btn btn-secondary"
                    style={{ marginBottom: 24, fontSize: 13, padding: '6px 14px' }}
                    onClick={() => navigate(-1)}
                >
                    <ArrowLeft size={14} /> Back
                </button>

                {actionError && <div className="alert alert-error" style={{ marginBottom: 16 }}>{actionError}</div>}

                <div className="card" style={{ marginBottom: 24 }}>
                    <div className="flex items-center justify-between mb-6">
                        <div className="flex items-center gap-3">
                            <span className="text-muted text-sm">#{request.id}</span>
                            <StatusBadge status={request.status} />
                            {isTriageOrAdmin && STATUS_TRANSITIONS[request.status].length > 0 && (
                                <div className="flex gap-2" style={{ marginLeft: 8 }}>
                                    {STATUS_TRANSITIONS[request.status].map(next => (
                                        <button
                                            key={next}
                                            className="btn btn-secondary"
                                            style={{ padding: '3px 10px', fontSize: 11 }}
                                            disabled={statusMutation.isPending}
                                            onClick={() => statusMutation.mutate(next)}
                                        >
                                            → {next.replace('_', ' ')}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                        <div className="flex items-center gap-2">
                            <span className="text-xs text-muted">{formatDate(request.createdAt)}</span>
                            {canManageRequestThread && (
                                <button
                                    type="button"
                                    className="btn btn-danger"
                                    style={{ padding: '5px 10px', fontSize: 12 }}
                                    onClick={handleDeleteRequest}
                                    disabled={requestDeleteMutation.isPending}
                                >
                                    <Trash2 size={12} />
                                    Delete Request
                                </button>
                            )}
                        </div>
                    </div>

                    <h2 style={{ fontSize: '1.3rem', fontWeight: 700, marginBottom: 12 }}>{request.title}</h2>
                    <p style={{ color: 'var(--color-text-muted)', lineHeight: 1.8, whiteSpace: 'pre-wrap' }}>
                        {request.description}
                    </p>

                    <div className="request-detail-grid">
                        <div>
                            <p className="text-xs text-muted" style={{ marginBottom: 4 }}>Created by</p>
                            <p style={{ fontWeight: 500, fontSize: 14 }}>{request.createdBy.username}</p>
                        </div>
                        <div>
                            <p className="text-xs text-muted" style={{ marginBottom: 4 }}>Assigned to</p>
                            {isTriageOrAdmin ? (
                                <AssigneeSelect
                                    requestId={request.id}
                                    currentAssigneeId={request.assignedTo?.id}
                                />
                            ) : (
                                <p style={{ fontWeight: 500, fontSize: 14 }}>
                                    {request.assignedTo?.username ?? <span style={{ color: 'var(--color-text-subtle)' }}>Unassigned</span>}
                                </p>
                            )}
                        </div>
                        <div>
                            <p className="text-xs text-muted" style={{ marginBottom: 4 }}>Last updated</p>
                            <p style={{ fontWeight: 500, fontSize: 14 }}>{formatDate(request.updatedAt)}</p>
                        </div>
                    </div>

                    <div className="request-attachments-panel">
                        <AttachmentList
                            title="Request Attachments"
                            attachments={requestAttachments}
                            onDownload={handleRequestAttachmentDownload}
                            onDelete={canManageRequestThread ? handleDeleteRequestAttachment : undefined}
                            isDeletingId={deletingRequestAttachmentId}
                        />
                        {canManageRequestThread && (
                            <AttachmentUploader
                                scopeLabel="request"
                                existingCount={requestAttachments.length}
                                maxCount={REQUEST_ATTACHMENT_LIMIT}
                                maxFileSizeBytes={ATTACHMENT_MAX_FILE_SIZE_BYTES}
                                allowedMimeTypes={ATTACHMENT_ALLOWED_MIME_TYPES}
                                createUploadUrl={(file) =>
                                    createRequestAttachmentUploadUrl(reqId, {
                                        fileName: file.name,
                                        contentType: file.type || 'application/octet-stream',
                                        fileSize: file.size,
                                    })
                                }
                                confirmUpload={(attachmentId) => confirmRequestAttachment(reqId, attachmentId).then(() => undefined)}
                                onCompleted={() => {
                                    queryClient.invalidateQueries({ queryKey: ['request-attachments', reqId] })
                                }}
                            />
                        )}
                    </div>
                </div>

                <div className="card">
                    <div className="flex items-center gap-2" style={{ marginBottom: 20 }}>
                        <MessageSquare size={16} color="var(--color-primary)" />
                        <h3>Comments ({comments?.totalElements ?? 0})</h3>
                    </div>

                    {comments && comments.content.length === 0 && (
                        <p className="text-muted text-sm" style={{ marginBottom: 20 }}>
                            No comments yet. Be the first to comment.
                        </p>
                    )}

                    <div>
                        {comments?.content.map((comment) => {
                            const commentAttachments = commentAttachmentsById.get(comment.id) ?? []
                            const canDeleteComment = isTriageOrAdmin || user?.username === comment.author.username

                            return (
                                <div key={comment.id} className="comment">
                                    <div className="comment-meta">
                                        <span className="comment-author">{comment.author.username}</span>
                                        {comment.author.roles.map((role) => (
                                            <span key={role} className={`role-badge role-${role}`}>{role}</span>
                                        ))}
                                        <span className="comment-time">{formatDate(comment.createdAt)}</span>
                                        {canDeleteComment && (
                                            <button
                                                type="button"
                                                className="btn btn-danger"
                                                style={{ padding: '3px 8px', fontSize: 11, marginLeft: 'auto' }}
                                                onClick={() => handleDeleteComment(comment)}
                                                disabled={deletingCommentId === comment.id}
                                            >
                                                <Trash2 size={11} />
                                                Delete
                                            </button>
                                        )}
                                    </div>
                                    <p className="comment-body">{comment.body}</p>

                                    <div className="comment-attachments-panel">
                                        <AttachmentList
                                            title="Comment Attachments"
                                            attachments={commentAttachments}
                                            onDownload={(attachment) => handleCommentAttachmentDownload(comment.id, attachment)}
                                            onDelete={canManageRequestThread
                                                ? (attachment) => handleDeleteCommentAttachment(comment.id, attachment)
                                                : undefined}
                                            isDeletingId={deletingCommentAttachmentKey?.startsWith(`${comment.id}-`)
                                                ? Number(deletingCommentAttachmentKey.split('-')[1])
                                                : null}
                                        />
                                        {canManageRequestThread && (
                                            <AttachmentUploader
                                                scopeLabel="comment"
                                                existingCount={commentAttachments.length}
                                                maxCount={COMMENT_ATTACHMENT_LIMIT}
                                                maxFileSizeBytes={ATTACHMENT_MAX_FILE_SIZE_BYTES}
                                                allowedMimeTypes={ATTACHMENT_ALLOWED_MIME_TYPES}
                                                createUploadUrl={(file) =>
                                                    createCommentAttachmentUploadUrl(reqId, comment.id, {
                                                        fileName: file.name,
                                                        contentType: file.type || 'application/octet-stream',
                                                        fileSize: file.size,
                                                    })
                                                }
                                                confirmUpload={(attachmentId) =>
                                                    confirmCommentAttachment(reqId, comment.id, attachmentId).then(() => undefined)}
                                                onCompleted={() => {
                                                    queryClient.invalidateQueries({
                                                        queryKey: ['comment-attachments', reqId, comment.id],
                                                    })
                                                }}
                                            />
                                        )}
                                    </div>
                                </div>
                            )
                        })}
                    </div>

                    <form onSubmit={handleAddComment} style={{ marginTop: 24, display: 'flex', flexDirection: 'column', gap: 12 }}>
                        {commentError && <div className="alert alert-error">{commentError}</div>}
                        <textarea
                            id="comment-body"
                            className="form-textarea"
                            placeholder="Add a comment…"
                            value={commentBody}
                            onChange={(e) => setCommentBody(e.target.value)}
                            rows={3}
                            disabled={commentMutation.isPending}
                        />
                        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                            <button
                                id="submit-comment-btn"
                                type="submit"
                                className="btn btn-primary"
                                disabled={commentMutation.isPending || !commentBody.trim()}
                            >
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
