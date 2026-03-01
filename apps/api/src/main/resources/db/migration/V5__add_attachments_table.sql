-- V5: Attachment metadata for request-level and comment-level files

CREATE TABLE attachments (
    id            BIGSERIAL PRIMARY KEY,
    request_id    BIGINT REFERENCES support_requests(id) ON DELETE CASCADE,
    comment_id    BIGINT REFERENCES comments(id) ON DELETE CASCADE,
    file_name     VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(255)  NOT NULL,
    file_size     BIGINT        NOT NULL CHECK (file_size > 0),
    s3_object_key VARCHAR(1024) NOT NULL UNIQUE,
    state         VARCHAR(20)   NOT NULL,
    uploaded_by   BIGINT        NOT NULL REFERENCES users(id),
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_attachments_exactly_one_parent CHECK (
        (request_id IS NOT NULL AND comment_id IS NULL) OR
        (request_id IS NULL AND comment_id IS NOT NULL)
    ),
    CONSTRAINT chk_attachments_state CHECK (state IN ('PENDING', 'ACTIVE', 'FAILED'))
);

CREATE INDEX idx_attachments_request_id    ON attachments(request_id);
CREATE INDEX idx_attachments_comment_id    ON attachments(comment_id);
CREATE INDEX idx_attachments_state         ON attachments(state);
CREATE INDEX idx_attachments_uploaded_by   ON attachments(uploaded_by);
CREATE INDEX idx_attachments_created_at    ON attachments(created_at);
