import { Link } from 'react-router-dom'
import { Clock, User, MessageSquare } from 'lucide-react'
import StatusBadge from './StatusBadge'
import type { RequestStatus } from '../api/requests'

export interface RequestSummary {
    id: number
    title: string
    status: string
    createdByUsername: string
    assignedToUsername?: string
    commentCount?: number
    createdAt: string
    updatedAt: string
}

interface Props {
    request: RequestSummary
}

function timeAgo(isoDate: string): string {
    const diff = Date.now() - new Date(isoDate).getTime()
    const mins = Math.floor(diff / 60000)
    if (mins < 1) return 'just now'
    if (mins < 60) return `${mins}m ago`
    const hrs = Math.floor(mins / 60)
    if (hrs < 24) return `${hrs}h ago`
    const days = Math.floor(hrs / 24)
    return `${days}d ago`
}

const RequestCard: React.FC<Props> = ({ request }) => {
    return (
        <Link to={`/requests/${request.id}`} className="request-card" style={{ textDecoration: 'none', display: 'block' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                    <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600, color: 'var(--color-text)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {request.title}
                    </h3>
                    <div style={{ display: 'flex', gap: 16, marginTop: 8, flexWrap: 'wrap' }}>
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--color-text-muted)' }}>
                            <User size={12} />
                            {request.createdByUsername}
                        </span>
                        {request.assignedToUsername && (
                            <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--color-text-muted)' }}>
                                Assigned: {request.assignedToUsername}
                            </span>
                        )}
                        {typeof request.commentCount === 'number' && (
                            <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--color-text-muted)' }}>
                                <MessageSquare size={12} />
                                {request.commentCount}
                            </span>
                        )}
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--color-text-muted)' }}>
                            <Clock size={12} />
                            {timeAgo(request.updatedAt)}
                        </span>
                    </div>
                </div>
                <StatusBadge status={request.status as RequestStatus} />
            </div>
        </Link>
    )
}

export default RequestCard
