CREATE TABLE file_download_audits
(
    id                         BIGSERIAL PRIMARY KEY,
    file_id_snapshot           UUID                     NOT NULL,
    user_id_snapshot           BIGINT                   NOT NULL,
    user_email_snapshot        VARCHAR(255)             NOT NULL,
    original_filename_snapshot VARCHAR(255)             NOT NULL,
    event_type                 VARCHAR(30)              NOT NULL,
    request_ip                 VARCHAR(45),
    user_agent                 VARCHAR(500),
    occurred_at                TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_file_download_audit_event_type
        CHECK (event_type IN ('DOWNLOAD_GRANTED'))
);

CREATE INDEX idx_download_audits_user_time
    ON file_download_audits (
                             user_id_snapshot,
                             occurred_at DESC
        );

CREATE INDEX idx_download_audits_file_time
    ON file_download_audits (
                             file_id_snapshot,
                             occurred_at DESC
        );