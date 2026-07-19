CREATE TABLE stored_files
(
    id                UUID                     PRIMARY KEY,
    owner_id          BIGINT                   NOT NULL,
    title             VARCHAR(150)             NOT NULL,
    description       TEXT,
    category          VARCHAR(50)              NOT NULL,
    original_filename VARCHAR(255)             NOT NULL,
    stored_filename   VARCHAR(100)             NOT NULL,
    extension         VARCHAR(20)              NOT NULL,
    media_type        VARCHAR(150)             NOT NULL,
    size_bytes        BIGINT                   NOT NULL,
    relative_path     VARCHAR(500)             NOT NULL,
    sha256            CHAR(64),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_stored_files_owner
        FOREIGN KEY (owner_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT uk_stored_files_stored_filename
        UNIQUE (stored_filename),

    CONSTRAINT uk_stored_files_relative_path
        UNIQUE (relative_path),

    CONSTRAINT chk_stored_files_size
        CHECK (size_bytes > 0)
);

CREATE INDEX idx_stored_files_owner_created_at
    ON stored_files (owner_id, created_at DESC);

CREATE INDEX idx_stored_files_owner_category
    ON stored_files (owner_id, category);