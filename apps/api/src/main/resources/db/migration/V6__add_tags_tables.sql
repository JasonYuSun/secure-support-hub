-- V6: Tags dictionary and request-tag association tables

CREATE TABLE tags (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    created_by  BIGINT        NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP     NULL
);

-- Case-insensitive uniqueness among active (non-deleted) tags
CREATE UNIQUE INDEX uidx_tags_name_active
    ON tags (lower(name))
    WHERE deleted_at IS NULL;

CREATE INDEX idx_tags_created_by ON tags(created_by);
CREATE INDEX idx_tags_deleted_at ON tags(deleted_at);

CREATE TABLE request_tags (
    request_id  BIGINT    NOT NULL REFERENCES support_requests(id) ON DELETE CASCADE,
    tag_id      BIGINT    NOT NULL REFERENCES tags(id),
    applied_by  BIGINT    NOT NULL REFERENCES users(id),
    applied_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id, tag_id)
);

CREATE INDEX idx_request_tags_request_id ON request_tags(request_id);
CREATE INDEX idx_request_tags_tag_id     ON request_tags(tag_id);
