ALTER TABLE members ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'ROLE_USER'
    CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'));

CREATE TABLE login_sessions (
    id UUID PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    refresh_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_login_sessions_member ON login_sessions(member_id);
CREATE INDEX idx_login_sessions_expiry ON login_sessions(expires_at);
