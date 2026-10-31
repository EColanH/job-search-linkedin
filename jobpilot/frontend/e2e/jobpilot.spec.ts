import { expect, test } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const fixture = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../tests/fixtures/synthetic.docx');

test('candidate review, LinkedIn import, analysis, CV approval and application tracking', async ({ page, request }) => {
  expect(process.env.JOBPILOT_E2E_WRITE, 'Run against a disposable database with JOBPILOT_E2E_WRITE=1').toBe('1');
  const checkedPut = async (url: string, data: unknown) => {
    const response = await request.put(url, { data });
    expect(response.ok(), `${url}: ${await response.text()}`).toBeTruthy();
  };
  await checkedPut('/api/settings', { ai: { provider: 'DISABLED', model: 'synthetic-browser-test', temperature: .1, externalConsent: false } });
  await checkedPut('/api/candidates/current', { name: '', summary: '', verifiedFacts: [], pendingFacts: [] });
  await checkedPut('/api/search-preferences', { salaryFilterEnabled: false, minMatchScore: 0, excludedCompanies: [], excludedKeywords: [] });

  await page.goto('/profile');
  await page.getByLabel('Choose CV file').setInputFiles(fixture);
  await page.getByRole('button', { name: 'Upload and extract', exact: true }).click();
  const extracted = page.getByLabel('Edit extracted fact').last();
  await expect(extracted).toBeVisible();
  await extracted.fill('Resolved customer support escalations and collaborated with support teams.');
  await page.getByRole('button', { name: 'Verify fact', exact: true }).last().click();
  await page.getByLabel('Full name', { exact: true }).fill('Synthetic Browser Candidate');
  await page.getByRole('button', { name: 'Save verified profile', exact: true }).click();
  await expect(page.getByText('Your verified profile has been saved.', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Full name', { exact: true })).toHaveValue('Synthetic Browser Candidate');
  await expect(page.getByLabel('Edit verified Imported CV')).toHaveValue('Resolved customer support escalations and collaborated with support teams.');

  await page.getByRole('link', { name: 'Discover Jobs', exact: true }).click();
  await page.getByRole('button', { name: 'Import job', exact: true }).click();
  const dialog = page.getByRole('dialog');
  const linkedinId = String(Date.now());
  const jobTitle = `Synthetic Browser Customer Operations Specialist ${linkedinId}`;
  await dialog.getByLabel('LinkedIn job URL').fill(`https://www.linkedin.com/jobs/view/${linkedinId}/?trackingId=browser`);
  await dialog.getByLabel('Job title', { exact: true }).fill(jobTitle);
  await dialog.getByLabel('Company', { exact: true }).fill('Synthetic Browser Company');
  await dialog.getByLabel('Location', { exact: true }).fill('Kraków, Poland');
  await dialog.getByLabel('Full job description').fill('Resolve customer support escalations and collaborate with support teams. Communication and issue resolution skills are required. PMP certification is preferred.');
  await dialog.getByRole('button', { name: 'Import LinkedIn job', exact: true }).click();
  await expect(page).toHaveURL(/\/jobs\//);
  await expect(page.getByRole('heading', { name: jobTitle, exact: true })).toBeVisible();
  await expect(page.getByText('Salary not disclosed', { exact: true }).first()).toBeVisible();

  await page.getByRole('button', { name: 'Analyze job', exact: true }).click();
  await expect(page.getByRole('tab', { name: 'AI Analysis', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('heading', { name: 'Supporting evidence', exact: true })).toBeVisible();
  await expect(page.getByText('Not evidenced in the verified profile: PMP certification', { exact: true })).toBeVisible();

  await page.getByRole('tab', { name: 'Recruiter', exact: true }).click();
  await expect(page.getByText('Recruiter not identified.', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Generate message drafts', exact: true }).click();
  await expect(page.getByLabel('Linkedin draft')).toHaveValue(/Resolved customer support escalations/);
  await page.getByLabel('Linkedin draft').fill('Synthetic browser-reviewed draft; no message was sent.');
  const messageContainer = page.getByLabel('Linkedin draft').locator('..');
  await messageContainer.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByText('Message draft saved.', { exact: true })).toBeVisible();

  await page.getByRole('tab', { name: 'Tailored CV', exact: true }).click();
  await page.getByRole('button', { name: 'Generate tailored CV', exact: true }).click();
  await expect(page.getByLabel('Tailored CV draft')).toContainText('Resolved customer support escalations');
  await expect(page.getByRole('link', { name: 'Download PDF', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Approve CV', exact: true }).click();
  const pdfDownload = page.waitForEvent('download');
  await page.getByRole('link', { name: 'Download PDF', exact: true }).click();
  const pdf = await pdfDownload;
  expect(pdf.suggestedFilename()).toMatch(/\.pdf$/);
  expect(await pdf.failure()).toBeNull();
  const docxDownload = page.waitForEvent('download');
  await page.getByRole('link', { name: 'Download DOCX', exact: true }).click();
  const docx = await docxDownload;
  expect(docx.suggestedFilename()).toMatch(/\.docx$/);
  expect(await docx.failure()).toBeNull();

  await page.getByRole('button', { name: 'Track application', exact: true }).click();
  await page.getByRole('button', { name: 'Update application', exact: true }).click();
  const applicationDialog = page.getByRole('dialog');
  await applicationDialog.getByLabel('Application status', { exact: true }).selectOption('APPLIED');
  await expect(applicationDialog.getByRole('button', { name: 'Save application', exact: true })).toBeDisabled();
  await applicationDialog.getByLabel('Confirm application submitted').check();
  await applicationDialog.getByLabel('Notes', { exact: true }).fill('Synthetic candidate confirmed submission outside JobPilot.');
  await applicationDialog.getByRole('button', { name: 'Save application', exact: true }).click();
  await expect(applicationDialog).not.toBeVisible();
  await page.getByRole('link', { name: 'Applications', exact: true }).click();
  await expect(page.getByRole('button', { name: `Update ${jobTitle}`, exact: true })).toBeVisible();
  await page.getByRole('button', { name: `Update ${jobTitle}`, exact: true }).click();
  await expect(page.getByRole('dialog').getByLabel('Application status', { exact: true })).toHaveValue('APPLIED');
  await expect(page.getByRole('dialog').getByLabel('Notes', { exact: true })).toHaveValue('Synthetic candidate confirmed submission outside JobPilot.');
});
