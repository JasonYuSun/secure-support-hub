import type { RequestStatus } from '../api/requests'

const labels: Record<RequestStatus, string> = {
    OPEN: 'Open',
    IN_PROGRESS: 'In Progress',
    RESOLVED: 'Resolved',
    CLOSED: 'Closed',
}

const StatusBadge: React.FC<{ status: RequestStatus }> = ({ status }) => (
    <span className={`badge badge-${status.toLowerCase()}`}>
        {labels[status]}
    </span>
)

export default StatusBadge
