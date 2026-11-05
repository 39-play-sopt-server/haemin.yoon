CREATE TABLE members (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    legacy_post_id BIGINT UNIQUE
);

-- 이름만으로 동일인 여부를 알 수 없으므로 기존 게시글마다 회원을 생성합니다.
INSERT INTO members (name, created_at, updated_at, legacy_post_id)
SELECT author, created_at, updated_at, id FROM posts;

ALTER TABLE posts ADD COLUMN member_id BIGINT;
UPDATE posts SET member_id = (SELECT id FROM members WHERE legacy_post_id = posts.id);
ALTER TABLE posts ALTER COLUMN member_id SET NOT NULL;
ALTER TABLE posts ADD CONSTRAINT fk_posts_member FOREIGN KEY (member_id) REFERENCES members(id);
CREATE INDEX idx_posts_member_id ON posts(member_id);

ALTER TABLE posts DROP COLUMN author;
ALTER TABLE members DROP COLUMN legacy_post_id;
