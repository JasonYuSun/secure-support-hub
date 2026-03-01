import React, { useMemo, useRef, useState } from 'react'
import axios from 'axios'
import { FileUp, RotateCcw, X } from 'lucide-react'

type UploadStatus = 'uploading' | 'confirming' | 'done' | 'error' | 'cancelled'

interface UploadItem {
    id: string
    file: File
    status: UploadStatus
    progress: number
    error?: string
    attachmentId?: number
    abortController?: AbortController
}

interface UploadUrlResponse {
    attachmentId: number
    uploadUrl: string
}

interface AttachmentUploaderProps {
    scopeLabel: string
    existingCount: number
    maxCount: number
    maxFileSizeBytes: number
    allowedMimeTypes: string[]
    createUploadUrl: (file: File) => Promise<UploadUrlResponse>
    confirmUpload: (attachmentId: number) => Promise<void>
    onCompleted: () => void
    disabled?: boolean
}

const ACTIVE_UPLOAD_STATUSES = new Set<UploadStatus>(['uploading', 'confirming'])

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

const formatUploadError = (error: unknown): string => {
    if (axios.isAxiosError(error)) {
        const status = error.response?.status
        if (!status) {
            return 'Upload failed due to network/CORS issue. Check connectivity and retry.'
        }
        if (status === 403) {
            return 'Upload URL expired or forbidden. Retry to request a new upload URL.'
        }
        if (status >= 500) {
            return 'Upload service temporarily unavailable. Retry in a moment.'
        }
        return error.response?.data?.message ?? 'Upload failed. Please retry.'
    }

    if (error instanceof DOMException && error.name === 'AbortError') {
        return 'Upload cancelled.'
    }

    return 'Upload failed unexpectedly. Please retry.'
}

const AttachmentUploader: React.FC<AttachmentUploaderProps> = ({
    scopeLabel,
    existingCount,
    maxCount,
    maxFileSizeBytes,
    allowedMimeTypes,
    createUploadUrl,
    confirmUpload,
    onCompleted,
    disabled = false,
}) => {
    const [isDragOver, setIsDragOver] = useState(false)
    const [uploads, setUploads] = useState<UploadItem[]>([])
    const fileInputRef = useRef<HTMLInputElement>(null)

    const allowedSet = useMemo(() => new Set(allowedMimeTypes.map((m) => m.toLowerCase())), [allowedMimeTypes])

    const inProgressCount = uploads.filter((item) => ACTIVE_UPLOAD_STATUSES.has(item.status)).length
    const remainingCount = Math.max(0, maxCount - existingCount - inProgressCount)

    const updateUpload = (id: string, updater: (current: UploadItem) => UploadItem) => {
        setUploads((current) => current.map((item) => (item.id === id ? updater(item) : item)))
    }

    const appendUpload = (upload: UploadItem) => {
        setUploads((current) => [upload, ...current])
    }

    const removeUpload = (id: string) => {
        setUploads((current) => current.filter((item) => item.id !== id))
    }

    const runUpload = async (id: string, file: File) => {
        const abortController = new AbortController()

        updateUpload(id, (item) => ({
            ...item,
            status: 'uploading',
            progress: 0,
            error: undefined,
            attachmentId: undefined,
            abortController,
        }))

        try {
            const uploadUrlResponse = await createUploadUrl(file)
            updateUpload(id, (item) => ({
                ...item,
                attachmentId: uploadUrlResponse.attachmentId,
                progress: 5,
            }))

            await axios.put(uploadUrlResponse.uploadUrl, file, {
                headers: {
                    'Content-Type': file.type || 'application/octet-stream',
                },
                signal: abortController.signal,
                onUploadProgress: (evt) => {
                    const total = evt.total ?? file.size
                    const progress = total > 0 ? Math.min(99, Math.round((evt.loaded * 100) / total)) : 0
                    updateUpload(id, (item) => ({ ...item, progress }))
                },
            })

            updateUpload(id, (item) => ({
                ...item,
                status: 'confirming',
                progress: 100,
            }))

            await confirmUpload(uploadUrlResponse.attachmentId)

            updateUpload(id, (item) => ({
                ...item,
                status: 'done',
                progress: 100,
                abortController: undefined,
            }))
            onCompleted()

            window.setTimeout(() => {
                removeUpload(id)
            }, 1200)
        } catch (error) {
            const cancelled = axios.isCancel(error) ||
                (error instanceof Error && error.name === 'CanceledError') ||
                (error instanceof DOMException && error.name === 'AbortError')

            if (cancelled) {
                updateUpload(id, (item) => ({
                    ...item,
                    status: 'cancelled',
                    progress: 0,
                    error: 'Upload cancelled.',
                    abortController: undefined,
                }))
                return
            }

            updateUpload(id, (item) => ({
                ...item,
                status: 'error',
                error: formatUploadError(error),
                abortController: undefined,
            }))
        }
    }

    const startSingleFileUpload = (file: File) => {
        const id = `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
        const upload: UploadItem = {
            id,
            file,
            status: 'uploading',
            progress: 0,
        }
        appendUpload(upload)
        void runUpload(id, file)
    }

    const appendValidationError = (file: File, error: string) => {
        const id = `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
        appendUpload({
            id,
            file,
            status: 'error',
            progress: 0,
            error,
        })
    }

    const processSelectedFiles = (incomingFiles: File[]) => {
        if (disabled) return

        let availableSlots = remainingCount
        for (const file of incomingFiles) {
            if (availableSlots <= 0) {
                appendValidationError(
                    file,
                    `Attachment limit reached for ${scopeLabel} (${maxCount} files max).`
                )
                continue
            }

            if (file.size > maxFileSizeBytes) {
                appendValidationError(
                    file,
                    `File is too large (${formatBytes(file.size)}). Max allowed is ${formatBytes(maxFileSizeBytes)}.`
                )
                continue
            }

            const mime = file.type.toLowerCase()
            if (!allowedSet.has(mime)) {
                appendValidationError(file, `File type is not allowed (${file.type || 'unknown'}).`)
                continue
            }

            availableSlots -= 1
            startSingleFileUpload(file)
        }
    }

    const handleFileInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const files = event.target.files ? Array.from(event.target.files) : []
        processSelectedFiles(files)
        event.target.value = ''
    }

    const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
        event.preventDefault()
        setIsDragOver(false)
        const files = Array.from(event.dataTransfer.files || [])
        processSelectedFiles(files)
    }

    const handleRetry = (item: UploadItem) => {
        void runUpload(item.id, item.file)
    }

    const handleCancel = (item: UploadItem) => {
        item.abortController?.abort()
    }

    return (
        <div className="attachment-uploader">
            <div
                className={`uploader-dropzone ${isDragOver ? 'is-drag-over' : ''} ${disabled ? 'is-disabled' : ''}`}
                onDragOver={(event) => {
                    event.preventDefault()
                    if (!disabled) setIsDragOver(true)
                }}
                onDragLeave={() => setIsDragOver(false)}
                onDrop={handleDrop}
            >
                <input
                    ref={fileInputRef}
                    type="file"
                    multiple
                    onChange={handleFileInputChange}
                    style={{ display: 'none' }}
                    disabled={disabled}
                />
                <div className="uploader-main">
                    <FileUp size={18} />
                    <p>Drop files here or</p>
                    <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={() => fileInputRef.current?.click()}
                        disabled={disabled}
                    >
                        Choose files
                    </button>
                </div>
                <p className="uploader-hint">
                    {remainingCount} of {maxCount} slots available. Max {formatBytes(maxFileSizeBytes)} per file.
                </p>
            </div>

            {uploads.length > 0 && (
                <div className="uploader-list">
                    {uploads.map((item) => {
                        const canRetry = item.status === 'error' || item.status === 'cancelled'
                        const canCancel = item.status === 'uploading' || item.status === 'confirming'

                        return (
                            <div key={item.id} className={`uploader-item status-${item.status}`}>
                                <div className="uploader-item-main">
                                    <div>
                                        <p className="uploader-item-name">{item.file.name}</p>
                                        <p className="uploader-item-meta">
                                            {formatBytes(item.file.size)} · {item.status.toUpperCase()}
                                        </p>
                                    </div>

                                    <div className="uploader-item-actions">
                                        {canRetry && (
                                            <button
                                                type="button"
                                                className="btn btn-secondary"
                                                style={{ padding: '4px 10px', fontSize: 12 }}
                                                onClick={() => handleRetry(item)}
                                                disabled={disabled}
                                            >
                                                <RotateCcw size={12} />
                                                Retry
                                            </button>
                                        )}
                                        {canCancel && (
                                            <button
                                                type="button"
                                                className="btn btn-danger"
                                                style={{ padding: '4px 10px', fontSize: 12 }}
                                                onClick={() => handleCancel(item)}
                                            >
                                                <X size={12} />
                                                Cancel
                                            </button>
                                        )}
                                    </div>
                                </div>

                                <div className="uploader-progress-track">
                                    <div className="uploader-progress-fill" style={{ width: `${item.progress}%` }} />
                                </div>
                                {item.error && <p className="uploader-item-error">{item.error}</p>}
                            </div>
                        )
                    })}
                </div>
            )}
        </div>
    )
}

export default AttachmentUploader
