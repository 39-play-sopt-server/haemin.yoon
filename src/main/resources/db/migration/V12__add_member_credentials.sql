-- 기존 회원은 이메일·비밀번호가 없으므로 NULL을 허용합니다. 새 회원은 애플리케이션에서 필수로 검증합니다.
-- UNIQUE 제약은 NULL을 중복으로 보지 않으므로 기존 회원끼리는 충돌하지 않습니다.
ALTER TABLE members ADD COLUMN email TEXT;
ALTER TABLE members ADD COLUMN password_hash TEXT;
ALTER TABLE members ADD CONSTRAINT uk_members_email UNIQUE (email);
