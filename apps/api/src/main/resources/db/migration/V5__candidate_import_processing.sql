ALTER TABLE candidate_import
    ADD COLUMN confirmation_key uuid,
    ADD COLUMN confirmed_at timestamptz,
    ADD COLUMN processed_count integer NOT NULL DEFAULT 0 CHECK (processed_count >= 0),
    ADD COLUMN error_count integer NOT NULL DEFAULT 0 CHECK (error_count >= 0),
    ADD CONSTRAINT candidate_import_processing_counts_ck
        CHECK (processed_count + error_count <= valid_count);

ALTER TABLE candidate_import_row DROP CONSTRAINT candidate_import_row_status_check;
ALTER TABLE candidate_import_row DROP CONSTRAINT candidate_import_row_payload_ck;
ALTER TABLE candidate_import_row ALTER COLUMN status TYPE varchar(30);
ALTER TABLE candidate_import_row
    ADD COLUMN candidate_id uuid,
    ADD COLUMN application_id uuid,
    ADD COLUMN error_code varchar(80),
    ADD COLUMN error_message varchar(500),
    ADD COLUMN processed_at timestamptz,
    ADD CONSTRAINT candidate_import_row_status_check CHECK (
        status IN (
            'VALID', 'INVALID', 'DUPLICATE', 'PROCESSING', 'APPLICATION_CREATED',
            'FETCHING_RESUME', 'RESUME_QUARANTINED', 'SCAN_PENDING', 'EXTRACTING_TEXT',
            'COMPLETED', 'DUPLICATE_APPLICATION', 'SOURCE_AUTH_REQUIRED',
            'RESUME_FETCH_FAILED', 'UNSAFE_FILE', 'PERSISTENCE_FAILED'
        )
    ),
    ADD CONSTRAINT candidate_import_row_payload_ck CHECK (
        (status IN ('INVALID', 'DUPLICATE') AND normalized_payload IS NULL AND jsonb_array_length(issues) > 0)
        OR (status NOT IN ('INVALID', 'DUPLICATE') AND normalized_payload IS NOT NULL)
    ),
    ADD CONSTRAINT candidate_import_row_candidate_fk
        FOREIGN KEY (workspace_id, candidate_id) REFERENCES candidate(workspace_id, id),
    ADD CONSTRAINT candidate_import_row_application_fk
        FOREIGN KEY (workspace_id, application_id) REFERENCES application(workspace_id, id);

CREATE INDEX candidate_import_confirmed_idx
    ON candidate_import (status, updated_at, workspace_id, id)
    WHERE status IN ('CONFIRMED', 'PROCESSING');
