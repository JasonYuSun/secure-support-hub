import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createRequest } from '../api/requests'
import { ArrowLeft, Send } from 'lucide-react'

const CreateRequestPage: React.FC = () => {
    const navigate = useNavigate()
    const queryClient = useQueryClient()
    const [title, setTitle] = useState('')
    const [description, setDescription] = useState('')
    const [error, setError] = useState('')

    const mutation = useMutation({
        mutationFn: () => createRequest({ title, description }),
        onSuccess: (data) => {
            queryClient.invalidateQueries({ queryKey: ['requests'] })
            navigate(`/requests/${data.id}`)
        },
        onError: (err: unknown) => {
            const msg = (err as { response?: { data?: { message?: string } } })
                ?.response?.data?.message || 'Failed to create request'
            setError(msg)
        },
    })

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault()
        setError('')
        if (!title.trim()) { setError('Title is required'); return }
        if (!description.trim()) { setError('Description is required'); return }
        mutation.mutate()
    }

    return (
        <div className="page-wrapper">
            <div className="container" style={{ maxWidth: 680 }}>
                <button className="btn btn-secondary" style={{ marginBottom: 24, fontSize: 13, padding: '6px 14px' }}
                    onClick={() => navigate(-1)}>
                    <ArrowLeft size={14} /> Back
                </button>

                <div className="page-header">
                    <div>
                        <h1 className="page-title">New Support Request</h1>
                        <p className="page-subtitle">Describe your issue and we'll help you resolve it</p>
                    </div>
                </div>

                <div className="card" style={{ padding: 32 }}>
                    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                        {error && <div className="alert alert-error">{error}</div>}

                        <div className="form-group">
                            <label className="form-label" htmlFor="req-title">Title</label>
                            <input
                                id="req-title"
                                type="text"
                                className="form-input"
                                placeholder="Brief summary of the issue"
                                value={title}
                                onChange={e => setTitle(e.target.value)}
                                maxLength={255}
                                required
                            />
                            <span className="text-xs text-muted" style={{ textAlign: 'right' }}>{title.length}/255</span>
                        </div>

                        <div className="form-group">
                            <label className="form-label" htmlFor="req-description">Description</label>
                            <textarea
                                id="req-description"
                                className="form-textarea"
                                placeholder="Provide as much detail as possible — steps to reproduce, expected behavior, account info, etc."
                                value={description}
                                onChange={e => setDescription(e.target.value)}
                                rows={7}
                                required
                            />
                        </div>

                        <div className="flex gap-3" style={{ justifyContent: 'flex-end' }}>
                            <button type="button" className="btn btn-secondary" onClick={() => navigate(-1)}>
                                Cancel
                            </button>
                            <button type="submit" className="btn btn-primary" disabled={mutation.isPending}>
                                {mutation.isPending
                                    ? <><span className="spinner" style={{ width: 14, height: 14, borderWidth: 2 }} /> Submitting…</>
                                    : <><Send size={14} /> Submit Request</>}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    )
}

export default CreateRequestPage
