CREATE TABLE candidate_import (
    id uuid NOT NULL,
    workspace_id uuid NOT NULL,
    job_id uuid NOT NULL,
    created_by uuid NOT NULL,
    source_object_key varchar(500) NOT NULL CHECK (btrim(source_object_key) <> ''),
    file_name varchar(255) NOT NULL CHECK (btrim(file_name) <> ''),
    row_count integer NOT NULL CHECK (row_count BETWEEN 1 AND 2000),
    source_columns jsonb NOT NULL
        CHECK (jsonb_typeof(source_columns) = 'array' AND jsonb_array_length(source_columns) > 0),
    suggested_mapping jsonb NOT NULL CHECK (jsonb_typeof(suggested_mapping) = 'object'),
    mapping jsonb CHECK (mapping IS NULL OR jsonb_typeof(mapping) = 'object'),
    status varchar(30) NOT NULL CHECK (
        status IN (
            'UPLOADED', 'MAPPED', 'VALIDATING', 'PREVIEW_READY', 'CONFIRMED',
            'PROCESSING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED', 'CANCELLED'
        )
    ),
    valid_count integer NOT NULL DEFAULT 0 CHECK (valid_count >= 0),
    invalid_count integer NOT NULL DEFAULT 0 CHECK (invalid_count >= 0),
    duplicate_count integer NOT NULL DEFAULT 0 CHECK (duplicate_count >= 0),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    UNIQUE (workspace_id, id),
    CONSTRAINT candidate_import_counts_ck
        CHECK (valid_count + invalid_count + duplicate_count <= row_count),
    CONSTRAINT candidate_import_job_fk
        FOREIGN KEY (workspace_id, job_id) REFERENCES job(workspace_id, id),
    CONSTRAINT candidate_import_creator_membership_fk
        FOREIGN KEY (workspace_id, created_by)
        REFERENCES workspace_membership(workspace_id, user_id)
);

CREATE INDEX candidate_import_workspace_status_updated_idx
    ON candidate_import (workspace_id, status, updated_at DESC, id);
CREATE INDEX candidate_import_workspace_job_created_idx
    ON candidate_import (workspace_id, job_id, created_at DESC, id);

CREATE TABLE candidate_import_row (
    workspace_id uuid NOT NULL,
    import_id uuid NOT NULL,
    source_row_number integer NOT NULL CHECK (source_row_number >= 2),
    status varchar(20) NOT NULL CHECK (status IN ('VALID', 'INVALID', 'DUPLICATE')),
    normalized_payload jsonb
        CHECK (normalized_payload IS NULL OR jsonb_typeof(normalized_payload) = 'object'),
    issues jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(issues) = 'array'),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, import_id, source_row_number),
    CONSTRAINT candidate_import_row_import_fk
        FOREIGN KEY (workspace_id, import_id)
        REFERENCES candidate_import(workspace_id, id)
        ON DELETE CASCADE,
    CONSTRAINT candidate_import_row_payload_ck CHECK (
        (
            status = 'VALID'
            AND normalized_payload IS NOT NULL
            AND jsonb_array_length(issues) = 0
        )
        OR (
            status IN ('INVALID', 'DUPLICATE')
            AND normalized_payload IS NULL
            AND jsonb_array_length(issues) > 0
        )
    )
);

CREATE INDEX candidate_import_row_import_status_idx
    ON candidate_import_row (workspace_id, import_id, status, source_row_number);

ALTER TABLE candidate_import ENABLE ROW LEVEL SECURITY;
ALTER TABLE candidate_import FORCE ROW LEVEL SECURITY;
CREATE POLICY candidate_import_isolation ON candidate_import TO talon_app
    USING (workspace_id = talon_current_workspace_id())
    WITH CHECK (workspace_id = talon_current_workspace_id());

ALTER TABLE candidate_import_row ENABLE ROW LEVEL SECURITY;
ALTER TABLE candidate_import_row FORCE ROW LEVEL SECURITY;
CREATE POLICY candidate_import_row_isolation ON candidate_import_row TO talon_app
    USING (workspace_id = talon_current_workspace_id())
    WITH CHECK (workspace_id = talon_current_workspace_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON candidate_import, candidate_import_row TO talon_app;
