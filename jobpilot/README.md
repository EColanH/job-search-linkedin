# JobPilot

A local-first career research workspace. Import LinkedIn postings, verify candidate facts, compare opportunities, prepare CV versions and outreach drafts, and track actions you perform yourself.

**JobPilot never applies, contacts recruiters, sends messages or submits candidate information to employers.** LinkedIn automatic discovery is disabled: no authorized API credentials have been provided. Search links and manual imports remain available. No login automation or scraping is implemented.

## Run on an Apple Silicon Mac

Prerequisites: Docker Desktop (Apple Silicon), Docker Compose v2, Python 3 for first-run password generation. Optional: host-installed Ollama. All production images are multi-architecture; no emulation setting is needed.

```sh
cd jobpilot
./scripts/setup.sh
```

The script creates an ignored `.env` with a random database password and starts services. Or copy `.env.example` to `.env`, set a unique `POSTGRES_PASSWORD`, then run `docker compose up --build -d --wait`.

- UI: http://localhost:3000
- REST: http://localhost:8080/api
- OpenAPI: http://localhost:8080/swagger-ui/index.html

Only UI and backend ports are published, on `127.0.0.1`. PostgreSQL and the document worker are private to the Compose network. Do not expose this single-user application to a LAN or the Internet. Uploaded and generated documents and database files live in Docker volumes, not Git. `docker compose down` preserves them; **do not use `down -v` unless you intend to erase data**.

## First workflow

1. Open Candidate Profile and upload a PDF or DOCX. Originals are immutable. Extraction creates pending facts, not approved claims; review, correct and verify each fact.
2. Adjust Search Settings: titles, Kraków/Poland, work arrangements and filters. Salary filtering starts disabled; 15,000 PLN gross/month is a preference only. Undisclosed salaries remain visible by default.
3. Open generated LinkedIn searches in your browser. Copy an individual posting URL into Import, and paste its description and known fields. A URL alone does not fetch LinkedIn content. Availability remains unknown until you verify it.
4. Analyze the job. Read evidence, missing information and limitations, rather than relying on the score alone. Salary alignment is separate.
5. Add recruiter information only from public business information, including the evidence URL and confidence. If no evidence exists, use the LinkedIn people search link.
6. Generate a CV proposal, compare against the verified profile, and approve before downloading PDF/DOCX. Generated drafts are versioned and never replace originals.
7. Generate/edit/copy outreach drafts. You send any communication yourself.
8. Track an application only after you submit it yourself. Moving to Applied requires your confirmation.

## Local AI

Install Ollama directly on macOS for Apple Silicon acceleration, then:

```sh
ollama pull llama3.2
```

Ollama must be reachable by the backend via `OLLAMA_BASE_URL` in `.env` (default `http://host.docker.internal:11434`). Configure provider, model and temperature in AI Settings. No model download is bundled. Basic storage, import, document export and conservative evidence matching work without an LLM. Model availability is reported separately; fallback output must not be confused with a verified AI integration.

External AI requires explicit consent in AI Settings and environment configuration. Set `JOBPILOT_OPENAI_BASE_URL` and `JOBPILOT_OPENAI_API_KEY` on the backend via a private Compose override, never in source control. Only enable this if you accept that candidate facts and job text may leave your Mac. No external provider has been validated by this repository's automated tests.

## Development

Java 21, Maven 3.9+, Node.js 22+ and Python 3.12 are required when running services without Docker. See [architecture](docs/ARCHITECTURE.md) and [validation](docs/VALIDATION.md).

```sh
# Backend (requires PostgreSQL; override datasource credentials through environment)
cd backend && mvn test && mvn spring-boot:run
# Frontend (separate terminal)
cd frontend && npm ci && npm run dev -- --port 3000
# Document worker (separate terminal)
cd document-worker
python3.12 -m venv .venv
. .venv/bin/activate
pip install -r requirements-dev.txt
uvicorn app:app --host 127.0.0.1 --port 8000 --no-access-log
```

The frontend forwards `/api` to the backend. Use the existing repository checkout; no worktree is needed.

## Operations

```sh
make logs
make down
make up
make backup
# Destructive restore: back up first and only use your own trusted archive
JOBPILOT_RESTORE_CONFIRM=REPLACE_LOCAL_DATA make restore ARCHIVE=backups/jobpilot-TIMESTAMP.tar.gz
```

Backups include private candidate data; protect/encrypt them yourself. The backup script briefly stops writers to capture consistent database/document state, then restarts them. Restore replaces current state. Candidate deletion in Profile deletes local candidate/job records and document storage; backups are independent and must be deleted separately.

## Integration boundaries

No automatic job discovery, recruiter scraping, freshness checking against LinkedIn, application submission, or outreach sending is enabled. Public recruiter evidence is entered by the user. LinkedIn filters not expressible in search URLs are applied locally where recorded data exists. Manual imported salary is treated as user-transcribed advertised compensation; JobPilot does not estimate salary. Unknown currencies/periods cannot be converted or ranked as comparable salaries.

Generated CVs use verified facts conservatively. An AI model may select relevant existing fact IDs; it cannot introduce new factual claims. This favors auditability over unrestricted rewriting. Read the validation report for tested capabilities and remaining limitations before use.
