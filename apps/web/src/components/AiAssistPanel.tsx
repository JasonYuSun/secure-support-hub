import React, { useState } from 'react'
import { Sparkles, Tag, FileText, MessageSquare } from 'lucide-react'
import AiSummaryCard from './AiSummaryCard'
import AiTagsCard from './AiTagsCard'
import AiDraftCard from './AiDraftCard'

interface AiAssistPanelProps {
    requestId: number
    canManage: boolean
    isTriageOrAdmin: boolean
    onUseDraft?: (draft: string) => void
}

type ActiveTab = 'summary' | 'tags' | 'draft' | null

const AiAssistPanel: React.FC<AiAssistPanelProps> = ({ requestId, canManage, isTriageOrAdmin, onUseDraft }) => {
    const [activeTab, setActiveTab] = useState<ActiveTab>(null)

    // Users can only draft/summarize their own, but Triage/Admin can also suggest tags (already handled by RBAC on UI implicitly by useAuth in parent, but we can enable everything they have access to)
    const canUseTags = isTriageOrAdmin

    if (!canManage) return null // Should not see AI assist panel if no access to request

    return (
        <div className="card" style={{ marginBottom: 24, border: '1px solid var(--color-primary-light)' }}>
            <div className="flex items-center gap-2 mb-4">
                <Sparkles size={18} color="var(--color-primary)" />
                <h3 style={{ margin: 0 }}>AI Assist ✨</h3>
            </div>

            <div className="flex gap-2" style={{ marginBottom: activeTab ? 16 : 0 }}>
                <button
                    className={`btn ${activeTab === 'summary' ? 'btn-primary' : 'btn-secondary'}`}
                    style={{ padding: '6px 12px', fontSize: 13 }}
                    onClick={() => setActiveTab(activeTab === 'summary' ? null : 'summary')}
                >
                    <FileText size={14} /> Summarize
                </button>

                {canUseTags && (
                    <button
                        className={`btn ${activeTab === 'tags' ? 'btn-primary' : 'btn-secondary'}`}
                        style={{ padding: '6px 12px', fontSize: 13 }}
                        onClick={() => setActiveTab(activeTab === 'tags' ? null : 'tags')}
                    >
                        <Tag size={14} /> Suggest Tags
                    </button>
                )}

                <button
                    className={`btn ${activeTab === 'draft' ? 'btn-primary' : 'btn-secondary'}`}
                    style={{ padding: '6px 12px', fontSize: 13 }}
                    onClick={() => setActiveTab(activeTab === 'draft' ? null : 'draft')}
                >
                    <MessageSquare size={14} /> Draft Response
                </button>
            </div>

            {activeTab === 'summary' && <AiSummaryCard requestId={requestId} />}
            {activeTab === 'tags' && <AiTagsCard requestId={requestId} />}
            {activeTab === 'draft' && <AiDraftCard requestId={requestId} onUseDraft={onUseDraft} />}

        </div>
    )
}

export default AiAssistPanel
