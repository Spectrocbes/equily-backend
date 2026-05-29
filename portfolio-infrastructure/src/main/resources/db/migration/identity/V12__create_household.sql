CREATE TABLE identity.households (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    owner_id   UUID         NOT NULL REFERENCES identity.users(id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE identity.household_memberships (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id UUID        NOT NULL REFERENCES identity.households(id),
    user_id      UUID        NOT NULL REFERENCES identity.users(id),
    role         VARCHAR(20) NOT NULL,
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(household_id, user_id)
);
