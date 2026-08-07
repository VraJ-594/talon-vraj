CREATE ROLE talon_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

GRANT talon_app TO CURRENT_USER;
GRANT USAGE ON SCHEMA public TO talon_app;

CREATE FUNCTION talon_current_workspace_id()
RETURNS uuid
LANGUAGE sql
STABLE
SET search_path = pg_catalog
AS $$
    SELECT NULLIF(current_setting('app.current_workspace_id', true), '')::uuid
$$;

CREATE TABLE workspace (
    id uuid PRIMARY KEY,
    name varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    slug varchar(100) NOT NULL UNIQUE CHECK (slug = lower(btrim(slug))),
    default_timezone varchar(100) NOT NULL CHECK (btrim(default_timezone) <> ''),
    status varchar(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    retention_months integer NOT NULL DEFAULT 24 CHECK (retention_months BETWEEN 1 AND 120),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    email varchar(320) NOT NULL CHECK (btrim(email) <> ''),
    normalized_email varchar(320) NOT NULL UNIQUE
        CHECK (normalized_email = lower(btrim(normalized_email))),
    display_name varchar(200) NOT NULL CHECK (btrim(display_name) <> ''),
    password_hash varchar(100) NOT NULL
        CHECK (password_hash ~ '^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$'),
    status varchar(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    default_workspace_id uuid REFERENCES workspace(id),
    last_login_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE workspace_membership (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES workspace(id),
    user_id uuid NOT NULL REFERENCES app_user(id),
    role varchar(30) NOT NULL CHECK (role IN ('WORKSPACE_ADMIN', 'RECRUITER')),
    status varchar(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    joined_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, user_id),
    UNIQUE (workspace_id, id)
);

CREATE INDEX workspace_membership_user_workspace_idx
    ON workspace_membership (user_id, workspace_id);

CREATE TABLE refresh_session (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES workspace(id),
    user_id uuid NOT NULL REFERENCES app_user(id),
    token_hash varchar(200) NOT NULL UNIQUE CHECK (btrim(token_hash) <> ''),
    family_id uuid NOT NULL,
    parent_id uuid REFERENCES refresh_session(id),
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (expires_at > created_at),
    CHECK (used_at IS NULL OR used_at >= created_at),
    CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX refresh_session_family_idx ON refresh_session (family_id, created_at);
CREATE INDEX refresh_session_user_active_idx
    ON refresh_session (workspace_id, user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE job (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES workspace(id),
    title varchar(200) NOT NULL CHECK (btrim(title) <> ''),
    department_name varchar(200),
    status varchar(20) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'ON_HOLD', 'CLOSED')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, id)
);

CREATE INDEX job_workspace_status_title_idx ON job (workspace_id, status, title, id);

CREATE TABLE candidate (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES workspace(id),
    email varchar(320) NOT NULL CHECK (btrim(email) <> ''),
    normalized_email varchar(320) NOT NULL
        CHECK (normalized_email = lower(btrim(normalized_email))),
    first_name varchar(120) NOT NULL CHECK (btrim(first_name) <> ''),
    last_name varchar(120) NOT NULL CHECK (btrim(last_name) <> ''),
    phone varchar(50),
    location varchar(200),
    current_title varchar(200),
    current_company varchar(200),
    skills_text text,
    experience_months integer CHECK (experience_months IS NULL OR experience_months >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, normalized_email),
    UNIQUE (workspace_id, id)
);

CREATE INDEX candidate_workspace_name_idx
    ON candidate (workspace_id, lower(last_name), lower(first_name), id);
CREATE INDEX candidate_name_trgm_idx
    ON candidate USING gin ((first_name || ' ' || last_name) gin_trgm_ops);
CREATE INDEX candidate_email_trgm_idx ON candidate USING gin (normalized_email gin_trgm_ops);

CREATE TABLE application (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES workspace(id),
    candidate_id uuid NOT NULL,
    job_id uuid NOT NULL,
    stage varchar(50) NOT NULL CHECK (btrim(stage) <> ''),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'HIRED', 'REJECTED', 'WITHDRAWN')),
    source varchar(100),
    applied_at date NOT NULL,
    notice_days integer CHECK (notice_days IS NULL OR notice_days >= 0),
    available_from date,
    current_ctc_currency char(3),
    current_ctc_minor bigint,
    expected_ctc_currency char(3),
    expected_ctc_minor bigint,
    form_schema_version integer NOT NULL DEFAULT 1 CHECK (form_schema_version > 0),
    form_answers jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(form_answers) = 'object'),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT application_candidate_fk
        FOREIGN KEY (workspace_id, candidate_id) REFERENCES candidate(workspace_id, id),
    CONSTRAINT application_job_fk
        FOREIGN KEY (workspace_id, job_id) REFERENCES job(workspace_id, id),
    CONSTRAINT application_current_ctc_pair_ck CHECK (
        (current_ctc_currency IS NULL AND current_ctc_minor IS NULL)
        OR (current_ctc_currency ~ '^[A-Z]{3}$' AND current_ctc_minor >= 0)
    ),
    CONSTRAINT application_expected_ctc_pair_ck CHECK (
        (expected_ctc_currency IS NULL AND expected_ctc_minor IS NULL)
        OR (expected_ctc_currency ~ '^[A-Z]{3}$' AND expected_ctc_minor >= 0)
    ),
    UNIQUE (workspace_id, candidate_id, job_id),
    UNIQUE (workspace_id, id)
);

CREATE INDEX application_workspace_job_stage_idx
    ON application (workspace_id, job_id, stage, applied_at DESC, id);
CREATE INDEX application_workspace_expected_ctc_idx
    ON application (workspace_id, expected_ctc_currency, expected_ctc_minor, id);

ALTER TABLE workspace ENABLE ROW LEVEL SECURITY;
ALTER TABLE workspace FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON workspace TO talon_app
    USING (id = talon_current_workspace_id())
    WITH CHECK (id = talon_current_workspace_id());

ALTER TABLE workspace_membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE workspace_membership FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_membership_isolation ON workspace_membership TO talon_app
    USING (workspace_id = talon_current_workspace_id())
    WITH CHECK (workspace_id = talon_current_workspace_id());

ALTER TABLE refresh_session ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_session FORCE ROW LEVEL SECURITY;
CREATE POLICY refresh_session_isolation ON refresh_session TO talon_app
    USING (workspace_id = talon_current_workspace_id())
    WITH CHECK (workspace_id = talon_current_workspace_id());

ALTER TABLE job ENABLE ROW LEVEL SECURITY;
ALTER TABLE job FORCE ROW LEVEL SECURITY;
CREATE POLICY job_isolation ON job TO talon_app
    USING (workspace_id = talon_current_workspace_id())
    WITH CHECK (workspace_id = talon_current_workspace_id());

ALTER TABLE candidate ENABLE ROW LEVEL SECURITY;
ALTER TABLE candidate FORCE ROW LEVEL SECURITY;
CREATE POLICY candidate_isolation ON candidate TO talon_app
    USING (workspace_id = talon_current_workspace_id())
    WITH CHECK (workspace_id = talon_current_workspace_id());

ALTER TABLE application ENABLE ROW LEVEL SECURITY;
ALTER TABLE application FORCE ROW LEVEL SECURITY;
CREATE POLICY application_isolation ON application TO talon_app
    USING (workspace_id = talon_current_workspace_id())
    WITH CHECK (workspace_id = talon_current_workspace_id());

GRANT SELECT, INSERT, UPDATE ON app_user TO talon_app;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON workspace, workspace_membership, refresh_session, job, candidate, application
    TO talon_app;
