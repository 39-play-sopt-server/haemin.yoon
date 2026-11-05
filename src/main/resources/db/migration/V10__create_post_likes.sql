ALTER TABLE posts ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE posts ADD CONSTRAINT posts_like_count_nonnegative CHECK (like_count >= 0);

CREATE TABLE post_likes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uq_post_likes_post_member UNIQUE (post_id, member_id),
    CONSTRAINT fk_post_likes_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_post_likes_member FOREIGN KEY (member_id) REFERENCES members(id)
);

CREATE INDEX idx_post_likes_member_post ON post_likes (member_id, post_id);
