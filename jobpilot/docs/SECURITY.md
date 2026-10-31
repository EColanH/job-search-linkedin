# Security and privacy

JobPilot is a single-user application for a trusted personal computer. Docker Compose publishes the frontend and backend on `127.0.0.1` only; PostgreSQL and the document worker have no published host ports. Do not expose these services through a tunnel, public reverse proxy, LAN port mapping, or shared server. Authentication and per-user authorization are not implemented.

The backend restricts browser-origin requests to supported local origins. Local access is a trust boundary: software running as your local user can access the API, documents, Docker volumes, and database. Protect the Mac account and use FileVault. Browser request protections do not replace operating-system access controls.

## Candidate facts and CV approval

Uploaded PDF and DOCX documents are preserved as immutable originals. Extraction proposes literal paragraphs for review; it does not approve facts. Moving text into the verified profile is the user's assertion that the claim is accurate. Review employment dates, courses, qualifications, and section labels carefully. A Project Management course must not be described as PMP or PMI certification unless independently held and verified.

The current generation pipeline selects and reorders existing verified facts. The document worker rejects any generated section whose heading and text do not exactly match the supplied verified facts. Local model responses are restricted to existing fact IDs; unknown IDs and malformed output are rejected. Model output cannot add a qualification or change a factual claim. This conservative design limits rewriting functionality.

Generated CV versions remain drafts until the user approves them. The public backend download endpoint refuses PDF and DOCX downloads before approval. The worker is an internal service: its parsing, rendering, download, and deletion API is unauthenticated and must remain isolated on the Compose network. Anyone with local Docker or filesystem access can access drafts directly.

## Imported content and integrations

LinkedIn descriptions, recruiter details, and uploaded text are untrusted data. JobPilot does not execute that text or use it as instructions. The frontend displays text through React escaping. The worker escapes PDF markup, validates file types and contents, bounds upload size and decompressed DOCX size, rejects macro-enabled files, and refuses encrypted or textless PDFs. OCR is not implemented. PDF and document libraries should be updated regularly.

LinkedIn discovery is manual unless a future authorized integration is explicitly implemented. Importing a URL records the URL and pasted description; it does not verify that a posting is active. Recruiter confidence is based on user-supplied evidence and association confirmation, not an independent identity investigation.

JobPilot never submits an application, sends a message, requests a connection, or contacts a recruiter. Outreach remains an editable local draft. Marking an application as applied requires the user to confirm their external submission.

## AI providers and credentials

Ollama on the host is the preferred model provider. Basic application workflows do not require a running model. An external provider requires explicit consent in settings plus an operator-configured endpoint and API key. Enabling an external provider permits verified profile facts and job text needed by the operation to leave the machine; review the provider's data policy before consenting. Store credentials in local environment configuration, never in Git or candidate text.

The repository does not contain a real candidate CV or seeded private employment information. `.gitignore` excludes `.env`, the application data directory, backups, and dependency/build caches. Docker keeps database and document volumes outside the checkout; host Ollama keeps models outside it. Do not place personal documents, generated CVs, or model files into another repository path.

## Backups and deletion

Use `scripts/backup.sh` to pause writers and back up the database plus document volumes together. Archives contain personal data. Keep them out of Git and protect them with encrypted storage. `scripts/restore.sh` requires an explicit replacement acknowledgment and restores only a locally trusted archive. Restoring replaces existing application data.

The candidate deletion endpoint requires explicit confirmation and deletes local application records and document storage. Separate backup archives remain until you delete them. Check that no generation tasks are running before deletion; a request may be rejected while work is in progress.

## Validation limits

Security properties in this document describe implementation boundaries, not a penetration-test certification. Automated tests use synthetic data. See the project validation report for checks actually run and any remaining environment or deployment limitations. Public-network deployment or multiple users would require a separate security design with authentication, authorization, audited access, TLS, and stronger isolation.
