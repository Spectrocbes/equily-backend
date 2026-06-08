CREATE TABLE identity.user_preferences (
    user_id    UUID        NOT NULL PRIMARY KEY
                           REFERENCES identity.users(id) ON DELETE CASCADE,
    currency   VARCHAR(3)  NOT NULL DEFAULT 'EUR',
    locale     VARCHAR(5)  NOT NULL DEFAULT 'fr',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO identity.user_preferences (user_id, currency, locale)
SELECT id, 'EUR', 'fr' FROM identity.users;
