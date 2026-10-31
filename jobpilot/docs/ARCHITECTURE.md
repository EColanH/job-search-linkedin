# Architecture and trust boundaries

JobPilot runs on a single machine with four services: a React SPA, a Java 21 Spring Boot monolith, PostgreSQL 17 and a Python 3.12 FastAPI document worker. The document worker is internal and has no public port. Ollama runs on the host for GPU acceleration on macOS.

The REST service owns candidate verification, LinkedIn URL validation/deduplication, salary filtering, match analysis, recruiters/evidence, CV proposal approval, outreach drafts, settings and application tracking. Flyway owns database migration. PostgreSQL JSONB records retain versioned domain payloads with indexed kinds, identities and timestamps. This small initial deployment uses a shared record repository rather than an extensive relational reporting schema; referential/domain checks live in the service. Future relational migrations should preserve immutable document IDs and historical snapshots.

The worker accepts only PDF/DOCX and creates opaque immutable UUID storage directories. It validates archive structure and size, checks file type, extracts plain text without executing macros, and renders ATS documents. Uploaded original bytes are retained. Candidate extraction is conservative and requires review; it is not a certification or employment verification system. Text is treated as data throughout; the UI does not render imported HTML.

The AI boundary accepts untrusted job descriptions and a whitelist of verified candidate facts. Structured output may select existing IDs; unknown IDs and malformed output are rejected. Local fallback uses explicit textual evidence and discloses its method. A model cannot create verified facts, approve documents, send outreach or submit applications. User-entered verified facts remain the user's responsibility.

Long-running model/analysis/CV tasks have persistent state and bounded workers. A process restart cannot resume an in-flight model call; interrupted tasks must be surfaced and retried rather than reported as success. No scheduled crawler is activated without authorized LinkedIn access.

This is a single-user local application with same-origin request protections, localhost bindings and internal service isolation. It is not a multi-tenant authentication system. Do not publish its ports externally. Secrets belong in environment variables and are never returned by settings endpoints. Logs should contain no CV contents, prompts or provider credentials.

## Data lifecycle

Originals and generated documents use a persistent volume. Database metadata uses a separate PostgreSQL volume. Candidate facts become available for CV generation only after explicit review. Each generated CV stores its candidate snapshot/proposal and must be approved before export. Application actions are user-reported history, not proof of an external submission. Recruiter confidence reports the supplied evidence and association, not independent identity verification.
