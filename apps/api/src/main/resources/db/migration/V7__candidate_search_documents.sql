ALTER TABLE candidate
    ADD COLUMN search_document tsvector GENERATED ALWAYS AS (
        to_tsvector(
            'simple',
            coalesce(first_name, '') || ' ' ||
            coalesce(last_name, '') || ' ' ||
            coalesce(normalized_email, '') || ' ' ||
            coalesce(location, '') || ' ' ||
            coalesce(current_title, '') || ' ' ||
            coalesce(current_company, '') || ' ' ||
            coalesce(skills_text, '')
        )
    ) STORED;

CREATE INDEX candidate_search_document_gin_idx
    ON candidate USING gin (search_document);

CREATE INDEX candidate_workspace_location_idx
    ON candidate (workspace_id, lower(location), id);

CREATE INDEX candidate_workspace_experience_idx
    ON candidate (workspace_id, experience_months DESC, id);

CREATE INDEX application_workspace_applied_at_idx
    ON application (workspace_id, applied_at DESC, id);

CREATE INDEX application_workspace_notice_idx
    ON application (workspace_id, notice_days, id);

CREATE INDEX application_workspace_available_idx
    ON application (workspace_id, available_from, id);
