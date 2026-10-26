ALTER TABLE posts ADD COLUMN updated_at TIMESTAMP(6) WITHOUT TIME ZONE;

-- 기존 게시글의 마지막 수정 시각은 알 수 없으므로 생성 시각으로 초기화합니다.
UPDATE posts SET updated_at = created_at;

ALTER TABLE posts ALTER COLUMN updated_at SET NOT NULL;
