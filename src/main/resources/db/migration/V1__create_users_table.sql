CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    full_name     VARCHAR(100)             NOT NULL,
    email         VARCHAR(255)             NOT NULL,
    password_hash VARCHAR(255)             NOT NULL,
    role          VARCHAR(20)              NOT NULL DEFAULT 'USER',
    enabled       BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_users_email
        UNIQUE (email),

    CONSTRAINT chk_users_role
        CHECK (role IN ('USER', 'ADMIN'))
);