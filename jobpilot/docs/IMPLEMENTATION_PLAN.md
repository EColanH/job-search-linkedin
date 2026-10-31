# JobPilot implementation plan

1. Foundation: Java 21/Spring Boot REST service, PostgreSQL/Flyway, React/TypeScript, Python document service, localhost Docker Compose, candidate profile and immutable originals.
2. LinkedIn jobs: canonical URL validation, deduplication, manual descriptions, generated search links, honest integration status.
3. Matching: verified candidate facts, conservative evidence scoring, configurable weights, optional local Ollama, explicit consent for external providers.
4. CV Studio: versioned proposals, comparison, approval gate, validated PDF/DOCX exports.
5. Recruiter intelligence: user-supplied public evidence, confidence classification, editable outreach drafts only.
6. Applications: persistent board, explicit confirmation of externally submitted applications, history and follow-ups.
7. Validation and operations: automated component tests, cross-service smoke, restart persistence, documented Mac deployment, backups and deletion.

Every phase includes implementation, compilation, tests and documentation. No automated LinkedIn retrieval is enabled without an authorized integration. No messages, applications or candidate information are submitted externally by JobPilot.

## Architecture

Modular Spring Boot monolith owns persistence and domain rules. PostgreSQL 17 stores application state and metadata. FastAPI parses and renders documents to a persistent local volume. React consumes the REST API through a same-origin reverse proxy. Host Ollama is optional and preferred on Apple Silicon. Services publish ports only on 127.0.0.1.
