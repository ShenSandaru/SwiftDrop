-- Owner: identity module. User profile avatar stored as BYTEA binary data.

ALTER TABLE users ADD COLUMN avatar_photo BYTEA;
ALTER TABLE users ADD COLUMN avatar_content_type VARCHAR(50);
ALTER TABLE users ADD COLUMN avatar_updated_at TIMESTAMPTZ;
