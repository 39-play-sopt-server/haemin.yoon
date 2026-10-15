ALTER TABLE posts
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE posts
    ADD CONSTRAINT posts_view_count_nonnegative CHECK (view_count >= 0);
