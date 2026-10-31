-- Aggregate documents provide versioned domain storage; kind separates bounded contexts.
CREATE TABLE domain_records (
 id varchar(255) PRIMARY KEY,
 kind varchar(255) NOT NULL CHECK (kind IN ('candidate','preferences','settings','job','recruiter','cv','message','application','application_event','execution','task')),
 source_key varchar(255) UNIQUE,
 data jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 version bigint NOT NULL DEFAULT 0
);
CREATE INDEX records_kind_idx ON domain_records(kind);
CREATE INDEX records_created_idx ON domain_records(kind,created_at DESC);
CREATE INDEX records_job_reference_idx ON domain_records((data->>'jobId'));
CREATE UNIQUE INDEX applications_one_per_job ON domain_records((data->>'jobId')) WHERE kind='application';
