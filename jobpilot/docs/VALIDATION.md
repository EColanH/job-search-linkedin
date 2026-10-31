# Validation report

Validated on Linux/x86_64 in the development environment, 2026-10-31. This is not evidence of a macOS/Apple Silicon execution or a live LinkedIn/AI provider test.

## Component checks

- Backend: Java21 compile/package and24 JUnit tests passed, zero failures/errors/skips. Includes API validation, hostile Host/Origin requests, approval and application confirmation gates, salary filtering/alignment, structured AI output rejection, conservative qualification handling, and an actual PostgreSQL17 Testcontainers migration/JSONB/deduplication test.
- Document worker:12 pytest tests passed. Synthetic PDF/DOCX extraction, byte-identical originals, exact verified-fact generation, PDF/DOCX roundtrips, Polish Unicode, immutable versions, malformed/macro/encrypted/oversized documents, page/decompression bounds and path safety.
- Frontend:11 Vitest/React Testing Library tests passed. API errors, multipart upload, task completion/failure, salary display, accessible controls/dialogs/errors, and stable form labels.
- Production TypeScript/Vite build passed. Desktop,390px mobile, light and dark modal layouts visually inspected; no page errors/global mobile overflow were observed.
- OpenAPI generated31 endpoint paths and typed candidate/import request schemas.

## Cross-service checks

`python scripts/smoke.py --base-url http://127.0.0.1:8080 --allow-write` passed45 real API checks against the running PostgreSQL/backend/worker. This exercises upload, pending/verified facts, original preservation, LinkedIn-only URL validation/deduplication, unknown availability, salary off/on/undisclosed behavior, async matching, salary-independent score, recruiter evidence, CV approval/download validity, four editable outreach types, explicit applied confirmation, persisted application notes/interview/history and dashboard counts. Host/Origin protections are tested too.

One comprehensive Playwright browser test passed: upload→review/save/reload→manual LinkedIn import→analysis→message generation/edit→CV draft/approval→PDF+DOCX downloads→confirmed application→persisted tracker status/notes. The final static-container browser run also passed after UI label improvements (1 test,7.5seconds; no API mocks).

Tests use synthetic candidate data only. Neither the real candidate's CV nor scraped job content is included in fixtures. Run write tests only against disposable local data; the smoke script requires explicit `--allow-write`.

## Deployment and operations

Container image builds passed with TLS verification intact. Cloud-only proxy settings and CA files were supplied as temporary BuildKit secrets; no session credentials or CA were committed to the application. Compose interpolation/validation and shell syntax checks passed. Restore refuses an archive containing `../database.dump` before touching services.

All four production Compose services became healthy. The final container API smoke passed all45 checks. Restarting every service preserved candidate/job/application/settings records and approved PDF/DOCX SHA-256 hashes exactly. Container validation caught and fixed non-root worker file permissions and a proxy-sensitive BusyBox loopback health check. Backup and restore results are recorded below.

## Deliberate limits and remaining feature work

This is a functional initial implementation, not full production feature parity with the master specification:

- No authorized automatic LinkedIn discovery/recruiter retrieval or remote freshness verification was available. Search links and manual posting/evidence entry are operational. Scheduling is disabled rather than backed by an unauthorized crawler.
- Candidate extraction proposes literal paragraphs with manually assigned sections, not a fully structured employers/dates/education parser or OCR. Uploaded originals remain available.
- Suitability uses a limited, versioned phrase-evidence heuristic; it is not a calibrated hiring prediction. Optional Spring AI calls can select known fact IDs only. Neither host Ollama nor an external AI endpoint was running during validation.
- CV tailoring selects/reorders verified text; it does not provide free-form AI summary/responsibility rewriting or section prose regeneration. Comparison shows verified source content and the proposed output, not a visual rendering of original PDF pages. Large profiles may produce more than two pages; manually select relevant facts. Manual revisions create new drafts.
- Outreach suggestions are verified-fact templates with tone variation and editing, not live LLM-generated prose. Nothing is sent externally.
- Domain data currently uses indexed, versioned JSONB aggregates rather than the full normalized table design. Further domain service separation, schema-specific response DTOs and richer workflow validation are appropriate before a broader release.
- Single-user, loopback-only deployment; no multi-user authentication. Backups contain private data and are not automatically encrypted. External API keys must be configured outside Git.
- Mac/ARM compatibility follows multi-architecture base images; actual Apple Silicon deployment remains to be verified on the user's machine.

Recommended next work: live Ollama acceptance tests; structured candidate extraction with source spans; grounded prose proposals with explicit fact-by-fact review; richer CV diffs and page-budget controls; typed domain response contracts and normalized reporting tables; expanded browser negative-path coverage. Authorized LinkedIn discovery can only be added after actual API permission is granted.

## Final operations acceptance

- `docker compose up -d --wait`: all four services healthy.
- Final production-container API smoke:45/45 checks passed.
- Final production-container Playwright:1/1 comprehensive test passed (7.5s).
- Full service restart: persisted JSON records and approved PDF/DOCX byte hashes matched the pre-restart snapshot.
- Backup/restore: backup created successfully; synthetic application notes were deliberately changed; restoring the archive recovered the pre-change records and identical approved document hashes.
- Deletion: the confirmed candidate-data deletion endpoint erased synthetic candidate/job/document state; a blank candidate and salary-filter-disabled/undisclosed-included defaults were verified afterward. The temporary test backup and standalone test services were removed. Production Compose remains healthy.
- Git: implementation committed locally on `main`. Read-only Git operations succeeded, but GitHub REST/GraphQL metadata operations returned `Forbidden`; privacy was not verifiable and no remote push was performed.
