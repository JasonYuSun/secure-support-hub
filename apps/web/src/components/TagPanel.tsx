import React, { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
    fetchTags,
    fetchRequestTags,
    applyRequestTag,
    unapplyRequestTag,
    createTag,
    deleteTag,
} from '../api/tags'
import { X, Plus, Trash2, Tag as TagIcon } from 'lucide-react'

interface TagPanelProps {
    requestId: number
    canManage: boolean
    isTriageOrAdmin: boolean
}

const extractErrorMessage = (err: unknown, fallback: string): string => {
    const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
    return message || fallback
}

const TagPanel: React.FC<TagPanelProps> = ({ requestId, canManage, isTriageOrAdmin }) => {
    const queryClient = useQueryClient()
    const [newTagName, setNewTagName] = useState('')
    const [selectedTagId, setSelectedTagId] = useState<string>('')
    const [panelError, setPanelError] = useState('')

    const { data: allTags = [], isLoading: loadingAll } = useQuery({
        queryKey: ['tags'],
        queryFn: fetchTags,
    })

    const { data: appliedTags = [], isLoading: loadingApplied } = useQuery({
        queryKey: ['request-tags', requestId],
        queryFn: () => fetchRequestTags(requestId),
    })

    const appliedTagIds = new Set(appliedTags.map((t) => t.id))
    const availableToApply = allTags.filter((t) => !appliedTagIds.has(t.id))

    const applyMutation = useMutation({
        mutationFn: (tagId: number) => applyRequestTag(requestId, tagId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['request-tags', requestId] })
            setSelectedTagId('')
            setPanelError('')
        },
        onError: (err: unknown) => {
            setPanelError(extractErrorMessage(err, 'Failed to apply tag'))
        },
    })

    const unapplyMutation = useMutation({
        mutationFn: (tagId: number) => unapplyRequestTag(requestId, tagId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['request-tags', requestId] })
            setPanelError('')
        },
        onError: (err: unknown) => {
            setPanelError(extractErrorMessage(err, 'Failed to remove tag'))
        },
    })

    const createTagMutation = useMutation({
        mutationFn: (name: string) => createTag(name),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['tags'] })
            setNewTagName('')
            setPanelError('')
        },
        onError: (err: unknown) => {
            setPanelError(extractErrorMessage(err, 'Failed to create tag'))
        },
    })

    const deleteTagMutation = useMutation({
        mutationFn: (tagId: number) => deleteTag(tagId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['tags'] })
            queryClient.invalidateQueries({ queryKey: ['request-tags', requestId] })
            setPanelError('')
        },
        onError: (err: unknown) => {
            setPanelError(extractErrorMessage(err, 'Failed to delete tag'))
        },
    })

    const handleApply = () => {
        if (!selectedTagId) return
        applyMutation.mutate(Number(selectedTagId))
    }

    const handleCreateTag = (e: React.FormEvent) => {
        e.preventDefault()
        if (!newTagName.trim()) return
        createTagMutation.mutate(newTagName.trim())
    }

    const isLoading = loadingAll || loadingApplied

    return (
        <div className="card" style={{ marginBottom: 24 }}>
            <div className="flex items-center gap-2" style={{ marginBottom: 16 }}>
                <TagIcon size={16} color="var(--color-primary)" />
                <h3>Tags</h3>
            </div>

            {panelError && (
                <div className="alert alert-error" style={{ marginBottom: 12 }}>
                    {panelError}
                </div>
            )}

            {/* Applied tags */}
            {isLoading ? (
                <div className="spinner" style={{ width: 16, height: 16, borderWidth: 2, marginBottom: 12 }} />
            ) : (
                <div
                    id="applied-tags-list"
                    style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: appliedTags.length > 0 ? 16 : 8 }}
                >
                    {appliedTags.length === 0 && (
                        <span className="text-muted text-sm">No tags applied yet.</span>
                    )}
                    {appliedTags.map((tag) => (
                        <span
                            key={tag.id}
                            className="tag-chip"
                            style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 4,
                                background: 'var(--color-primary-subtle, rgba(99,102,241,0.15))',
                                color: 'var(--color-primary)',
                                borderRadius: 20,
                                padding: '3px 10px',
                                fontSize: 12,
                                fontWeight: 500,
                            }}
                        >
                            {tag.name}
                            {canManage && (
                                <button
                                    type="button"
                                    aria-label={`Remove tag ${tag.name}`}
                                    style={{
                                        background: 'none',
                                        border: 'none',
                                        cursor: 'pointer',
                                        padding: 0,
                                        display: 'flex',
                                        color: 'inherit',
                                        opacity: 0.7,
                                    }}
                                    onClick={() => unapplyMutation.mutate(tag.id)}
                                    disabled={unapplyMutation.isPending}
                                >
                                    <X size={12} />
                                </button>
                            )}
                        </span>
                    ))}
                </div>
            )}

            {/* Apply existing tag */}
            {canManage && availableToApply.length > 0 && (
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 16 }}>
                    <select
                        id="tag-select"
                        className="form-input"
                        style={{ flex: 1, padding: '6px 10px', fontSize: 13 }}
                        value={selectedTagId}
                        onChange={(e) => setSelectedTagId(e.target.value)}
                        disabled={applyMutation.isPending}
                    >
                        <option value="">Select a tag to apply…</option>
                        {availableToApply.map((tag) => (
                            <option key={tag.id} value={tag.id}>
                                {tag.name}
                            </option>
                        ))}
                    </select>
                    <button
                        id="apply-tag-btn"
                        type="button"
                        className="btn btn-primary"
                        style={{ padding: '6px 14px', fontSize: 13, whiteSpace: 'nowrap' }}
                        onClick={handleApply}
                        disabled={!selectedTagId || applyMutation.isPending}
                    >
                        {applyMutation.isPending ? (
                            <span className="spinner" style={{ width: 12, height: 12, borderWidth: 2 }} />
                        ) : (
                            <>
                                <Plus size={13} /> Apply
                            </>
                        )}
                    </button>
                </div>
            )}

            {/* TRIAGE/ADMIN: tag dictionary management */}
            {isTriageOrAdmin && (
                <details style={{ marginTop: 8 }}>
                    <summary
                        style={{
                            cursor: 'pointer',
                            fontSize: 12,
                            color: 'var(--color-text-muted)',
                            userSelect: 'none',
                            marginBottom: 10,
                        }}
                    >
                        Manage Tag Dictionary
                    </summary>

                    <form
                        onSubmit={handleCreateTag}
                        style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12 }}
                    >
                        <input
                            id="new-tag-input"
                            type="text"
                            className="form-input"
                            placeholder="New tag name…"
                            style={{ flex: 1, padding: '5px 10px', fontSize: 13 }}
                            value={newTagName}
                            onChange={(e) => setNewTagName(e.target.value)}
                            disabled={createTagMutation.isPending}
                            maxLength={100}
                        />
                        <button
                            id="create-tag-btn"
                            type="submit"
                            className="btn btn-secondary"
                            style={{ padding: '5px 12px', fontSize: 13, whiteSpace: 'nowrap' }}
                            disabled={!newTagName.trim() || createTagMutation.isPending}
                        >
                            {createTagMutation.isPending ? (
                                <span className="spinner" style={{ width: 12, height: 12, borderWidth: 2 }} />
                            ) : (
                                <>
                                    <Plus size={12} /> Create
                                </>
                            )}
                        </button>
                    </form>

                    <div id="tag-dictionary-list" style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                        {allTags.map((tag) => (
                            <div
                                key={tag.id}
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                    padding: '4px 8px',
                                    background: 'var(--color-surface-raised, rgba(255,255,255,0.05))',
                                    borderRadius: 6,
                                    fontSize: 13,
                                }}
                            >
                                <span>{tag.name}</span>
                                <button
                                    type="button"
                                    aria-label={`Delete tag ${tag.name}`}
                                    className="btn btn-danger"
                                    style={{ padding: '2px 8px', fontSize: 11 }}
                                    onClick={() => {
                                        if (window.confirm(`Delete tag "${tag.name}" from dictionary?`)) {
                                            deleteTagMutation.mutate(tag.id)
                                        }
                                    }}
                                    disabled={deleteTagMutation.isPending}
                                >
                                    <Trash2 size={11} />
                                </button>
                            </div>
                        ))}
                        {allTags.length === 0 && (
                            <p className="text-muted text-sm">No tags in dictionary yet.</p>
                        )}
                    </div>
                </details>
            )}
        </div>
    )
}

export default TagPanel
