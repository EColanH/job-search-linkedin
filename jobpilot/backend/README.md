# Backend

Java 21 / Spring Boot 3.5 modular REST monolith. PostgreSQL 17 owns application state; the internal Python worker owns original and generated document bytes. `domain_records` stores versioned JSONB aggregates identified by kind, with database constraints for canonical LinkedIn jobs and one application per job. This keeps schemas flexible while the product is evolving; Flyway owns all DDL.

Run the complete application from `jobpilot/` using Docker Compose as described in the root README. To develop this service directly:

```sh
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jobpilot
export SPRING_DATASOURCE_USERNAME=jobpilot
export SPRING_DATASOURCE_PASSWORD=your-local-password
export DOCUMENT_WORKER_URL=http://localhost:8000
mvn test
mvn spring-boot:run
```

Swagger: `/swagger-ui/index.html`; machine-readable contracts: `/v3/api-docs`; readiness: `/actuator/health`.

Job analysis, document generation/revisions, and outreach drafts return a task record. Poll `GET /api/tasks/{id}` until `COMPLETED` (the `result` field contains the output) or `FAILED` (`error` contains a safe message). Only two tasks execute concurrently, with at most twenty waiting. Interrupted tasks are marked failed at startup so callers can retry intentionally.

Uploaded text starts in `pendingFacts`. A candidate profile update explicitly promotes facts to `verifiedFacts`. CV generation uses a snapshot of those verified facts, retains their exact text, and reorders sections by relevant keyword overlap. New prose must first be reviewed as candidate facts. Every revision creates a new draft; PDF/DOCX downloads return 409 until approval. Original documents remain separate.

Matching is an explicitly labeled conservative evidence heuristic. Optional Spring AI Ollama or OpenAI-compatible calls can select existing fact IDs as advisory evidence, but cannot add factual claims or modify the score. External AI requires explicit saved consent plus an HTTPS endpoint/API key configured through environment variables. No live model is required by basic workflows.

LinkedIn access is manual: no crawler, login automation, automated messages, or applications exist. Recruiters must be manually associated with public evidence; VERIFIED additionally requires explicit confirmation of the association. An APPLIED transition requires `confirmApplied: true`, and the dashboard counts only explicitly confirmed submissions.

HTTP binds to loopback by default. Compose overrides the container bind address while publishing only on host loopback. Host/origin checks reject remote browser requests, including cross-site requests. Local API clients remain permitted; this is a personal single-user installation, not a multi-user network service.

`mvn test` runs API behavior/security and matching tests. The PostgreSQL Testcontainers test verifies real Flyway migrations, JSONB storage, and deduplication when Docker is available; it is explicitly skipped when Docker is absent. `../scripts/smoke.py` exercises the full stack with synthetic data and requires explicit `--allow-write`.
