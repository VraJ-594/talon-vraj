-- Talon ATS candidate/search demo seed
--
-- Run manually in the Supabase SQL Editor against a NON-PRODUCTION database.
-- Change only v_workspace_slug below. The workspace must already exist.
-- Rerunning this statement updates the same 36 synthetic candidates/applications.
-- No candidate_file rows are created because there are no matching private PDF objects.

DO $talon_seed$
DECLARE
    v_workspace_slug constant text := 'talon-demo';
    v_allow_non_demo_workspace constant boolean := false;
    v_workspace_id uuid;
    v_candidate_count integer;
    v_application_count integer;
BEGIN
    IF NOT v_allow_non_demo_workspace
       AND lower(v_workspace_slug) NOT LIKE '%demo%'
       AND lower(v_workspace_slug) NOT LIKE '%test%' THEN
        RAISE EXCEPTION
            'Refusing synthetic seed for non-demo workspace %. Set v_allow_non_demo_workspace=true only after reviewing the target.',
            v_workspace_slug;
    END IF;

    SELECT id
      INTO v_workspace_id
      FROM workspace
     WHERE slug = lower(btrim(v_workspace_slug));

    IF v_workspace_id IS NULL THEN
        RAISE EXCEPTION 'Workspace slug % was not found', v_workspace_slug;
    END IF;

    PERFORM set_config('app.current_workspace_id', v_workspace_id::text, true);
    EXECUTE 'SET LOCAL ROLE talon_app';

    INSERT INTO job(id, workspace_id, title, department_name, location, status)
    VALUES
        (md5(v_workspace_id::text || ':demo-job:platform')::uuid,
         v_workspace_id, 'Senior Platform Engineer', 'Engineering', 'Bengaluru', 'ACTIVE'),
        (md5(v_workspace_id::text || ':demo-job:frontend')::uuid,
         v_workspace_id, 'Frontend Product Engineer', 'Engineering', 'Remote', 'ACTIVE'),
        (md5(v_workspace_id::text || ':demo-job:data')::uuid,
         v_workspace_id, 'Data Platform Engineer', 'Data', 'Hyderabad', 'ACTIVE'),
        (md5(v_workspace_id::text || ':demo-job:design')::uuid,
         v_workspace_id, 'Product Experience Designer', 'Product', 'Pune', 'ACTIVE')
    ON CONFLICT (id) DO UPDATE SET
        title = EXCLUDED.title,
        department_name = EXCLUDED.department_name,
        location = EXCLUDED.location,
        status = 'ACTIVE',
        updated_at = now()
    WHERE job.workspace_id = EXCLUDED.workspace_id;

    WITH synthetic AS (
        SELECT
            n,
            (ARRAY[
                'Aarav','Aditi','Akash','Ananya','Arjun','Diya',
                'Ishaan','Kavya','Meera','Neel','Riya','Vihaan'
            ])[1 + ((n - 1) % 12)] AS first_name,
            (ARRAY[
                'Sharma','Iyer','Mehta','Rao','Kapoor','Nair',
                'Shah','Verma','Kulkarni','Menon','Patel','Singh'
            ])[1 + (((n - 1) / 12 + n - 1) % 12)] AS last_name,
            (ARRAY['Bengaluru','Pune','Hyderabad','Mumbai','Delhi NCR','Remote'])
                [1 + ((n - 1) % 6)] AS location,
            (ARRAY[
                'Senior Java Engineer','Platform Engineer','Frontend Engineer',
                'Data Engineer','Product Designer','Full Stack Engineer',
                'Site Reliability Engineer','Engineering Lead'
            ])[1 + ((n - 1) % 8)] AS current_title,
            (ARRAY[
                'Northstar Labs','Orbit Systems','Finch Works','Mosaic Studio',
                'Canvas Cloud','Atlas Digital','Cedar Analytics','Harbor Products'
            ])[1 + ((n - 1) % 8)] AS current_company,
            (ARRAY[
                'Java, Spring Boot, PostgreSQL',
                'Java, Kubernetes, AWS',
                'React, TypeScript, Design systems',
                'Python, Kafka, PostgreSQL',
                'Figma, User research, Prototyping',
                'Node.js, React, PostgreSQL',
                'Go, Kubernetes, Observability',
                'Java, Distributed systems, Leadership',
                'Python, dbt, Snowflake',
                'React, Accessibility, TypeScript'
            ])[1 + ((n - 1) % 10)] AS skills_text
        FROM generate_series(1, 36) AS series(n)
    )
    INSERT INTO candidate(
        id, workspace_id, email, normalized_email, first_name, last_name,
        phone, location, current_title, current_company, skills_text, experience_months)
    SELECT
        md5(v_workspace_id::text || ':demo-candidate:' || lpad(n::text, 2, '0'))::uuid,
        v_workspace_id,
        'candidate-demo-' || lpad(n::text, 2, '0') || '@example.test',
        'candidate-demo-' || lpad(n::text, 2, '0') || '@example.test',
        first_name,
        last_name,
        NULL,
        location,
        current_title,
        current_company,
        skills_text,
        24 + ((n * 7) % 121)
    FROM synthetic
    ON CONFLICT (workspace_id, normalized_email) DO UPDATE SET
        email = EXCLUDED.email,
        first_name = EXCLUDED.first_name,
        last_name = EXCLUDED.last_name,
        phone = EXCLUDED.phone,
        location = EXCLUDED.location,
        current_title = EXCLUDED.current_title,
        current_company = EXCLUDED.current_company,
        skills_text = EXCLUDED.skills_text,
        experience_months = EXCLUDED.experience_months,
        updated_at = now();

    WITH synthetic AS (
        SELECT
            n,
            'candidate-demo-' || lpad(n::text, 2, '0') || '@example.test' AS normalized_email,
            (ARRAY[0, 15, 30, 45, 60, 90])[1 + ((n - 1) % 6)] AS notice_days,
            (ARRAY['APPLIED','SCREENING','TECHNICAL_INTERVIEW','HIRING_MANAGER_REVIEW','OFFER_REVIEW'])
                [1 + ((n - 1) % 5)] AS stage,
            (ARRAY['SUPABASE_DEMO','CAREERS_PAGE','EMPLOYEE_REFERRAL','RECRUITER_OUTREACH'])
                [1 + ((n - 1) % 4)] AS source,
            (ARRAY['Remote','Hybrid','Office'])[1 + ((n - 1) % 3)] AS preferred_work_mode
        FROM generate_series(1, 36) AS series(n)
    )
    INSERT INTO application(
        id, workspace_id, candidate_id, job_id, stage, status, source, applied_at,
        notice_days, available_from, current_ctc_currency, current_ctc_minor,
        expected_ctc_currency, expected_ctc_minor, form_answers)
    SELECT
        md5(v_workspace_id::text || ':demo-application:' || lpad(s.n::text, 2, '0'))::uuid,
        v_workspace_id,
        c.id,
        CASE ((s.n - 1) % 4)
            WHEN 0 THEN md5(v_workspace_id::text || ':demo-job:platform')::uuid
            WHEN 1 THEN md5(v_workspace_id::text || ':demo-job:frontend')::uuid
            WHEN 2 THEN md5(v_workspace_id::text || ':demo-job:data')::uuid
            ELSE md5(v_workspace_id::text || ':demo-job:design')::uuid
        END,
        s.stage,
        'ACTIVE',
        s.source,
        DATE '2026-08-08' - ((s.n - 1) % 28),
        s.notice_days,
        DATE '2026-08-15' + s.notice_days,
        'INR',
        (12 + ((s.n * 3) % 25))::bigint * 10000000,
        'INR',
        (15 + ((s.n * 3) % 30))::bigint * 10000000,
        jsonb_build_object(
            'preferredWorkMode', s.preferred_work_mode,
            'syntheticRecord', 'true')
    FROM synthetic s
    JOIN candidate c
      ON c.workspace_id = v_workspace_id
     AND c.normalized_email = s.normalized_email
    ON CONFLICT (workspace_id, candidate_id, job_id) DO UPDATE SET
        stage = EXCLUDED.stage,
        status = EXCLUDED.status,
        source = EXCLUDED.source,
        applied_at = EXCLUDED.applied_at,
        notice_days = EXCLUDED.notice_days,
        available_from = EXCLUDED.available_from,
        current_ctc_currency = EXCLUDED.current_ctc_currency,
        current_ctc_minor = EXCLUDED.current_ctc_minor,
        expected_ctc_currency = EXCLUDED.expected_ctc_currency,
        expected_ctc_minor = EXCLUDED.expected_ctc_minor,
        form_answers = EXCLUDED.form_answers,
        updated_at = now();

    SELECT count(*)
      INTO v_candidate_count
      FROM candidate
     WHERE workspace_id = v_workspace_id
       AND normalized_email LIKE 'candidate-demo-%@example.test';

    SELECT count(*)
      INTO v_application_count
      FROM application a
      JOIN candidate c
        ON c.workspace_id = a.workspace_id
       AND c.id = a.candidate_id
     WHERE a.workspace_id = v_workspace_id
       AND c.normalized_email LIKE 'candidate-demo-%@example.test';

    RAISE NOTICE 'Talon synthetic seed ready: % candidates, % applications in workspace %',
        v_candidate_count, v_application_count, v_workspace_slug;
END
$talon_seed$;
