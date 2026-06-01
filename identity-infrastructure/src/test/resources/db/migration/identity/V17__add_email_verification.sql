-- Email verification tokens
CREATE TABLE identity.email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_email_verification_token_hash
    ON identity.email_verification_tokens(token_hash);

-- Add email_verified column to users
ALTER TABLE identity.users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Mark all existing users as verified (dev environment migration)
-- WHERE clause required: targets all rows explicitly
UPDATE identity.users SET email_verified = TRUE WHERE email_verified = FALSE;
