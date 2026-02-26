import { useState } from 'react'
import { Send } from 'lucide-react'
import UserAvatar from './UserAvatar'

export interface Comment {
    id: number
    body: string
    authorUsername: string
    createdAt: string
}

interface Props {
    comments: Comment[]
    onAddComment: (body: string) => Promise<void>
    isSubmitting?: boolean
}

function timeAgo(isoDate: string): string {
    const diff = Date.now() - new Date(isoDate).getTime()
    const mins = Math.floor(diff / 60000)
    if (mins < 1) return 'just now'
    if (mins < 60) return `${mins}m ago`
    const hrs = Math.floor(mins / 60)
    if (hrs < 24) return `${hrs}h ago`
    return `${Math.floor(hrs / 24)}d ago`
}

const CommentThread: React.FC<Props> = ({ comments, onAddComment, isSubmitting = false }) => {
    const [body, setBody] = useState('')
    const [error, setError] = useState<string | null>(null)

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault()
        const trimmed = body.trim()
        if (!trimmed) {
            setError('Comment cannot be empty.')
            return
        }
        setError(null)
        try {
            await onAddComment(trimmed)
            setBody('')
        } catch {
            setError('Failed to post comment. Please try again.')
        }
    }

    return (
        <div className="comment-thread">
            <h4 style={{ margin: '0 0 16px', fontSize: 14, fontWeight: 600, color: 'var(--color-text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Comments ({comments.length})
            </h4>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                {comments.length === 0 && (
                    <p style={{ color: 'var(--color-text-muted)', fontSize: 14, textAlign: 'center', padding: '24px 0' }}>
                        No comments yet. Be the first to comment.
                    </p>
                )}
                {comments.map(c => (
                    <div key={c.id} style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
                        <UserAvatar username={c.authorUsername} size={32} />
                        <div style={{ flex: 1, background: 'var(--color-surface-2, rgba(255,255,255,0.04))', borderRadius: 8, padding: '10px 14px', border: '1px solid var(--color-border)' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                                <span style={{ fontWeight: 600, fontSize: 13, color: 'var(--color-text)' }}>{c.authorUsername}</span>
                                <span style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>{timeAgo(c.createdAt)}</span>
                            </div>
                            <p style={{ margin: 0, fontSize: 14, color: 'var(--color-text)', lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>{c.body}</p>
                        </div>
                    </div>
                ))}
            </div>

            <form onSubmit={handleSubmit} style={{ marginTop: 24 }}>
                <div className="form-group">
                    <textarea
                        id="comment-body"
                        className="form-control"
                        rows={3}
                        placeholder="Write a comment…"
                        value={body}
                        onChange={e => setBody(e.target.value)}
                        disabled={isSubmitting}
                        style={{ resize: 'vertical', minHeight: 80 }}
                    />
                    {error && <p style={{ color: 'var(--color-error, #f87171)', fontSize: 13, marginTop: 4 }}>{error}</p>}
                </div>
                <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
                    <button
                        id="submit-comment-btn"
                        type="submit"
                        className="btn btn-primary"
                        disabled={isSubmitting || !body.trim()}
                        style={{ display: 'flex', alignItems: 'center', gap: 6 }}
                    >
                        <Send size={14} />
                        {isSubmitting ? 'Posting…' : 'Post Comment'}
                    </button>
                </div>
            </form>
        </div>
    )
}

export default CommentThread
