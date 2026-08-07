ALTER TABLE job
    ADD COLUMN location varchar(200) NOT NULL DEFAULT 'Unspecified';

ALTER TABLE job
    ADD CONSTRAINT job_location_not_blank_ck CHECK (btrim(location) <> '');

ALTER TABLE job
    ALTER COLUMN location DROP DEFAULT;
