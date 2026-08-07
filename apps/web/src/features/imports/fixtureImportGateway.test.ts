import { describe, expect, it } from 'vitest';

import { createFixtureImportGateway } from './fixtureImportGateway';
import type { ColumnMapping, ImportProblem } from './importGateway';

function csvFile(contents: string, name = 'applications.csv') {
  return new File([contents], name, { type: 'text/csv' });
}

const requiredMapping: ColumnMapping = {
  first_name: 'first_name',
  last_name: 'last_name',
  email: 'email',
  resume_drive_url: 'resume_drive_url',
};

describe('fixture import gateway', () => {
  it('counts quoted multiline values as one RFC 4180 data record', async () => {
    const gateway = createFixtureImportGateway();
    const file = csvFile(
      'first_name,last_name,email,resume_drive_url,source\r\n' +
        'One,Applicant,one@example.test,https://drive.google.com/file/d/one/view,"First line\r\nSecond line"\r\n' +
        'Two,Applicant,two@example.test,https://drive.google.com/file/d/two/view,Single line\r\n',
    );

    const draft = await gateway.uploadCsv({ jobId: 'job-1', file });

    expect(draft.rowCount).toBe(2);
    expect(draft.sourceColumns).toEqual([
      'first_name',
      'last_name',
      'email',
      'resume_drive_url',
      'source',
    ]);
  });

  it('accepts canonical headers case-insensitively and maps them automatically', async () => {
    const gateway = createFixtureImportGateway();
    const draft = await gateway.uploadCsv({
      jobId: 'job-1',
      file: csvFile(
        'FIRST_NAME,Last_Name,EMAIL,Resume_Drive_URL\r\n' +
          'One,Applicant,one@example.test,https://drive.google.com/file/d/one/view',
      ),
    });

    expect(draft.suggestedMapping).toEqual({
      FIRST_NAME: 'first_name',
      Last_Name: 'last_name',
      EMAIL: 'email',
      Resume_Drive_URL: 'resume_drive_url',
    });
  });

  it('rejects a CSV when any header is not part of the canonical schema', async () => {
    const gateway = createFixtureImportGateway();

    await expect(
      gateway.uploadCsv({
        jobId: 'job-1',
        file: csvFile(
          'first_name,last_name,email,resume_drive_url,Current Salary\r\n' +
            'One,Applicant,one@example.test,https://drive.google.com/file/d/one/view,1200000',
        ),
      }),
    ).rejects.toMatchObject({ code: 'UNSUPPORTED_SOURCE_COLUMN' });

    const accepted = await gateway.uploadCsv({
      jobId: 'job-1',
      file: csvFile(
        'first_name,last_name,email,resume_drive_url\r\n' +
          'One,Applicant,one@example.test,https://drive.google.com/file/d/one/view',
      ),
    });
    expect(accepted.id).toBe('fixture-import-001');
  });

  it('rejects a CSV that omits a required canonical header', async () => {
    const gateway = createFixtureImportGateway();

    await expect(
      gateway.uploadCsv({
        jobId: 'job-1',
        file: csvFile('first_name,last_name,email\r\nOne,Applicant,one@example.test'),
      }),
    ).rejects.toMatchObject({ code: 'MISSING_REQUIRED_COLUMN' });
  });

  it('rejects duplicate source headers before building the mapping UI', async () => {
    const gateway = createFixtureImportGateway();

    await expect(
      gateway.uploadCsv({
        jobId: 'job-1',
        file: csvFile(
          'first_name,last_name,EMAIL,email,resume_drive_url\r\n' +
            'One,Applicant,one@example.test,alternate@example.test,https://drive.google.com/file/d/one/view',
        ),
      }),
    ).rejects.toMatchObject({
      code: 'DUPLICATE_SOURCE_COLUMN',
    } satisfies Partial<ImportProblem>);
  });

  it('rejects duplicate canonical mappings even when required fields are present', async () => {
    const gateway = createFixtureImportGateway();
    const draft = await gateway.uploadCsv({
      jobId: 'job-1',
      file: csvFile(
        'first_name,last_name,email,resume_drive_url,phone\r\n' +
          'One,Applicant,one@example.test,https://drive.google.com/file/d/one/view,9999999999',
      ),
    });

    await expect(
      gateway.validate({
        importId: draft.id,
        mapping: { ...requiredMapping, phone: 'email' },
        retainUnmapped: false,
      }),
    ).rejects.toMatchObject({ code: 'DUPLICATE_MAPPING' } satisfies Partial<ImportProblem>);
  });

  it('keeps safe row metadata isolated for each opaque import ID', async () => {
    const gateway = createFixtureImportGateway();
    const first = await gateway.uploadCsv({
      jobId: 'job-1',
      file: csvFile(
        'first_name,last_name,email,resume_drive_url\r\n' +
          'One,Applicant,one@example.test,https://drive.google.com/file/d/one/view\r\n' +
          'Two,Applicant,two@example.test,https://drive.google.com/file/d/two/view',
      ),
    });
    const second = await gateway.uploadCsv({
      jobId: 'job-2',
      file: csvFile(
        'first_name,last_name,email,resume_drive_url\r\n' +
          'Three,Applicant,three@example.test,https://drive.google.com/file/d/three/view',
      ),
    });

    expect(second.id).not.toBe(first.id);
    expect((await gateway.getImport(first.id)).totalCount).toBe(2);
    expect((await gateway.getImport(second.id)).totalCount).toBe(1);
    expect((await gateway.getImport(second.id)).rows).toHaveLength(1);
  });

  it('keeps invalid and duplicate preview rows terminal in completed progress', async () => {
    const gateway = createFixtureImportGateway();
    const draft = await gateway.uploadCsv({
      jobId: 'job-1',
      file: csvFile(
        'first_name,last_name,email,resume_drive_url\r\n' +
          'One,Applicant,one@example.test,https://drive.google.com/file/d/one/view\r\n' +
          'Two,Applicant,two@example.test,https://drive.google.com/file/d/two/view\r\n' +
          'Three,Applicant,three@example.test,https://drive.google.com/file/d/three/view',
      ),
    });

    await gateway.validate({ importId: draft.id, mapping: requiredMapping, retainUnmapped: false });
    const completed = await gateway.getImport(draft.id);

    expect(completed.rows).toEqual([
      { rowNumber: 1, status: 'COMPLETED', retryable: false },
      {
        rowNumber: 2,
        status: 'INVALID',
        retryable: false,
        message: 'Resume URL is not an anonymously readable Drive PDF.',
      },
      {
        rowNumber: 3,
        status: 'DUPLICATE_APPLICATION',
        retryable: false,
        message: 'An application already exists for this job.',
      },
    ]);
  });
});
