import React from 'react'
import { Download, FileText, Trash2 } from 'lucide-react'
import type { Attachment } from '../api/requests'

interface AttachmentListProps {
    title: string
    attachments: Attachment[]
    onDownload: (attachment: Attachment) => void
    onDelete?: (attachment: Attachment) => void
    isDeletingId?: number | null
}

const formatBytes = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`
    const units = ['KiB', 'MiB', 'GiB']
    let value = bytes / 1024
    let unitIndex = 0
    while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024
        unitIndex += 1
    }
    return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[unitIndex]}`
}

const AttachmentList: React.FC<AttachmentListProps> = ({
    title,
    attachments,
    onDownload,
    onDelete,
    isDeletingId,
}) => {
    return (
        <div className="attachment-list-block">
            <div className="attachment-list-header">
                <h4>{title}</h4>
                <span className="text-xs text-muted">{attachments.length} file(s)</span>
            </div>

            {attachments.length === 0 ? (
                <p className="text-muted text-sm">No attachments uploaded yet.</p>
            ) : (
                <div className="attachment-list">
                    {attachments.map((attachment) => {
                        const isActive = attachment.state === 'ACTIVE'
                        return (
                            <div key={attachment.id} className="attachment-item">
                                <div className="attachment-item-meta">
                                    <FileText size={15} />
                                    <div>
                                        <p className="attachment-item-name">{attachment.fileName}</p>
                                        <p className="attachment-item-sub">
                                            {formatBytes(attachment.fileSize)} · {attachment.contentType} · {attachment.state}
                                        </p>
                                    </div>
                                </div>
                                <div className="attachment-item-actions">
                                    <button
                                        type="button"
                                        className="btn btn-secondary"
                                        style={{ padding: '4px 10px', fontSize: 12 }}
                                        onClick={() => onDownload(attachment)}
                                        disabled={!isActive}
                                        title={isActive ? 'Download attachment' : 'Attachment is not ready for download'}
                                    >
                                        <Download size={12} />
                                        Download
                                    </button>
                                    {onDelete && (
                                        <button
                                            type="button"
                                            className="btn btn-danger"
                                            style={{ padding: '4px 10px', fontSize: 12 }}
                                            onClick={() => onDelete(attachment)}
                                            disabled={isDeletingId === attachment.id}
                                        >
                                            <Trash2 size={12} />
                                            Delete
                                        </button>
                                    )}
                                </div>
                            </div>
                        )
                    })}
                </div>
            )}
        </div>
    )
}

export default AttachmentList
