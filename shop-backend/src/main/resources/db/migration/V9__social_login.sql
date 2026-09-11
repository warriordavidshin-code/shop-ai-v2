-- Social login (Kakao / Naver) identity fields.
-- LOCAL email/password accounts keep working; social accounts may omit password and some profile fields.

ALTER TABLE member ADD COLUMN auth_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE member ADD COLUMN provider_user_id VARCHAR(128);
ALTER TABLE member ADD COLUMN profile_image_url VARCHAR(512);

UPDATE member SET auth_provider = 'LOCAL' WHERE auth_provider IS NULL;

ALTER TABLE member ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE member ALTER COLUMN birth_date DROP NOT NULL;
ALTER TABLE member ALTER COLUMN gender DROP NOT NULL;
ALTER TABLE member ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE member ALTER COLUMN postcode DROP NOT NULL;
ALTER TABLE member ALTER COLUMN address1 DROP NOT NULL;

ALTER TABLE member DROP CONSTRAINT IF EXISTS ck_member_auth_provider;
ALTER TABLE member ADD CONSTRAINT ck_member_auth_provider
    CHECK (auth_provider IN ('LOCAL', 'KAKAO', 'NAVER'));

-- One social identity maps to one member. LOCAL rows keep provider_user_id NULL.
CREATE UNIQUE INDEX uq_member_provider_identity
    ON member (auth_provider, provider_user_id)
    WHERE provider_user_id IS NOT NULL;
