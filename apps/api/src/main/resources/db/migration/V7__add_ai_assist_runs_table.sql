CREATE TABLE ai_assist_runs (
    id UUID PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES support_requests(id) ON DELETE CASCADE,
    action_type VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model_id VARCHAR(100),
    prompt_version VARCHAR(50),
    input_snapshot JSONB,
    output_payload JSONB,
    status VARCHAR(50) NOT NULL,
    error_code VARCHAR(100),
    error_message TEXT,
    latency_ms BIGINT,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_assist_runs_request_id ON ai_assist_runs(request_id);
CREATE INDEX idx_ai_assist_runs_action_type ON ai_assist_runs(action_type);
CREATE INDEX idx_ai_assist_runs_created_at ON ai_assist_runs(created_at);
CREATE INDEX idx_ai_assist_runs_created_by ON ai_assist_runs(created_by);
