CREATE INDEX idx_posts_created_at_id ON posts (created_at DESC, id DESC);
CREATE INDEX idx_posts_category_created_at_id ON posts (category, created_at DESC, id DESC);

-- 카테고리별 최신순 조회용 인덱스로 교체합니다.
DROP INDEX idx_posts_category_id;
