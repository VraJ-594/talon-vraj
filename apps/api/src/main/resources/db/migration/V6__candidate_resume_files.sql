CREATE TABLE candidate_file (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL,
    application_id uuid NOT NULL,
    file_name varchar(255) NOT NULL CHECK (btrim(file_name) <> ''),
    object_key varchar(500) NOT NULL CHECK (btrim(object_key) <> ''),
    status varchar(30) NOT NULL CHECK (
        status IN ('QUARANTINED', 'SCAN_PENDING', 'CLEAN', 'UNSAFE', 'FAILED')
    ),
    content_type varchar(100) NOT NULL CHECK (btrim(content_type) <> ''),
    size_bytes bigint NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, id),
    UNIQUE (workspace_id, application_id),
    CONSTRAINT candidate_file_application_fk
        FOREIGN KEY (workspace_id, application_id) REFERENCES application(workspace_id, id)
);

CREATE INDEX candidate_file_workspace_status_idx
    ON candidate_file (workspace_id, status, updated_at, id);

ALTER TABLE candidate_file ENABLE ROW LEVEL SECURITY;
ALTER TABLE candidate_file FORCE ROW LEVEL SECURITY;
CREATE POLICY candidate_file_isolation ON candidate_file TO talon_app
    USING (workspace_id = talon_current_workspace_id())
    WITH CHECK (workspace_id = talon_current_workspace_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON candidate_file TO talon_app;

ALTER TABLE candidate_import_row
    ADD COLUMN resume_file_id uuid,
    ADD CONSTRAINT candidate_import_row_resume_file_fk
        FOREIGN KEY (workspace_id, resume_file_id) REFERENCES candidate_file(workspace_id, id);

ALTER TABLE candidate_import DROP CONSTRAINT candidate_import_processing_counts_ck;
ALTER TABLE candidate_import
    ADD CONSTRAINT candidate_import_processing_counts_ck CHECK (
        processed_count <= valid_count AND error_count <= processed_count
    );
