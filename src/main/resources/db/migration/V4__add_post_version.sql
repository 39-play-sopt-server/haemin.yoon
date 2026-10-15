ALTER TABLE posts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE posts
    ADD CONSTRAINT posts_version_nonnegative CHECK (version >= 0);
