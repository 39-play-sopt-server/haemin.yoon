CREATE INDEX idx_posts_member_created_at_id ON posts (member_id, created_at DESC, id DESC);
CREATE INDEX idx_posts_member_category_created_at_id ON posts (member_id, category, created_at DESC, id DESC);

-- 기존 member_id 인덱스는 외래 키 검사용으로 유지합니다(H2에서는 제약에 연결되어 있습니다).
