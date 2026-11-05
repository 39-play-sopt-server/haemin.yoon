ALTER TABLE posts ADD COLUMN author_name TEXT;

UPDATE posts
SET author_name = members.name
FROM members
WHERE members.id = posts.member_id;

ALTER TABLE posts ALTER COLUMN author_name SET NOT NULL;
