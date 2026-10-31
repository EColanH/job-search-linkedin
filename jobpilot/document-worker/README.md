# Document worker

This internal-only FastAPI service performs no external network requests. Run on
Python 3.12 with `pip install -r requirements.txt` and
`uvicorn app:app --host 127.0.0.1 --port 8000 --no-access-log`.
Set `DOCUMENT_STORAGE` to a private persistent directory. Docker Compose mounts
`/data`; the service runs as a non-root user. Do not publish this internal API.
Backend approval and confirmation rules protect user-facing download and deletion
operations.

## API contract

- `GET /health`: storage readiness.
- `POST /parse`, multipart `file`: PDF/DOCX only, at most 10 MB and 80 PDF pages.
  Returns `documentId`, `originalName`, `text`, `pendingFacts`, `profile`, and
  `warnings`. Each pending fact has `id`, `section`, and literal extracted `text`.
  Extraction never verifies facts or infers skills, dates, or certifications.
- `POST /generate`: JSON `profile: {name, verified: true, verifiedFacts:
  [{id,section,text}]}` with optional `sections: [{heading,text}]`. Every section
  must exactly match an approved fact's section/text. Reordering and selection are
  supported; unsupported factual rewrites are rejected. Returns `documentId`,
  `preview`, `pdfValid`, and `docxValid`.
- `GET /documents/{uuid}/{pdf|docx|original|preview}`: stream stored content.
  Paths are generated server-side from canonical UUIDs, never caller paths.
- `DELETE /documents`: internal candidate-deletion hook; removes stored versions
  and originals. The backend must require explicit user confirmation.

Every uploaded original and generated version receives an immutable UUID folder.
The backend stores corresponding document metadata in PostgreSQL. Generated PDF
and DOCX files are re-opened and checked to contain every selected verified claim.
PDF text uses an embedded DejaVu Sans font when available for Polish characters.

DOCX processing validates the archive, rejects macros, and limits decompressed
size and entry count. PDF processing does not execute embedded scripts. Scanned
image-only PDFs return a clear error: OCR is not implemented. Imported paragraph
section labels require user review. Page count depends on the amount of approved
content; no content is silently removed to force a two-page document.

Run synthetic upload, validation, security, generation, and round-trip tests with
`pip install -r requirements-dev.txt && python -m pytest -q`.
