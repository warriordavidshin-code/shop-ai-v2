-- Login identity is login_id. Email becomes optional contact info.
ALTER TABLE member ADD COLUMN login_id VARCHAR(32);

UPDATE member
SET login_id = 'user' || member_id::text
WHERE login_id IS NULL;

ALTER TABLE member ALTER COLUMN login_id SET NOT NULL;
ALTER TABLE member ADD CONSTRAINT uq_member_login_id UNIQUE (login_id);

ALTER TABLE member ALTER COLUMN email DROP NOT NULL;
