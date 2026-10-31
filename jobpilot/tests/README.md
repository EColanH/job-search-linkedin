# Cross-service validation

`python3 scripts/smoke.py --base-url http://127.0.0.1:8080 --allow-write`

Run only against a disposable JobPilot database and document volume. This test creates a synthetic candidate, jobs, recruiter evidence, document versions, message drafts, and application records. It replaces the current candidate and preferences while testing their persistence; it never contacts LinkedIn or sends outreach. It does not delete application data automatically.

The smoke uses Python's standard library. Uploaded DOCX bytes are generated in memory from synthetic text. Generated files are checked for PDF/DOCX signatures and document contents. The test reports each assertion and exits nonzero on failure. Component-level worker tests perform deeper parser/render validation.

## Browser workflow

With the complete stack running on a disposable database:

```sh
cd frontend
npm ci
npx playwright install chromium
JOBPILOT_E2E_WRITE=1 npm run test:e2e
```

Set `PLAYWRIGHT_BASE_URL` to change the frontend address. If Chromium is already installed, set `PLAYWRIGHT_CHROMIUM_EXECUTABLE` to its executable path instead of downloading a browser. The browser test uploads the synthetic DOCX fixture, verifies candidate text, imports a LinkedIn posting through the form, and exercises the real backend. No API mocking is used.

## Verified results in the development environment

- Real API smoke: **45 assertions passed**, using Spring Boot, PostgreSQL 17, and the document worker. Coverage includes hostile Host/Origin rejection, immutable CV originals, verified-fact gating, salary filtering, compensation-independent matching, deduplication, recruiter evidence, async CV generation, approval/download gates, valid Office/PDF files, outreach drafts, and application history.
- Browser workflow: **1 end-to-end test passed** in Chromium against the real services. Candidate upload/review, LinkedIn import, analysis, message editing, CV approval and both downloads, application confirmation, and persisted tracker notes were exercised.

These results validate the current development instance. Docker deployment and restart/backup validation are reported separately in the project documentation.
