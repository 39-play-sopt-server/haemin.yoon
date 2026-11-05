CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

-- ILIKE '%keyword%'에도 사용할 수 있는 제목 부분 검색용 인덱스입니다.
CREATE INDEX idx_posts_title_trgm ON posts USING gin (title public.gin_trgm_ops);
