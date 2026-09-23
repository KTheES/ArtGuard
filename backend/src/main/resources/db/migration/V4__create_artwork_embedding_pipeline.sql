CREATE TABLE embedding_job (
 id UUID PRIMARY KEY,
 artwork_id UUID NOT NULL REFERENCES artwork(id),
 model VARCHAR(100) NOT NULL,
 model_version VARCHAR(100) NOT NULL,
 preprocessing_version VARCHAR(120) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('QUEUED','COMPLETED','FAILED','CANCELED')),
 generation INTEGER NOT NULL DEFAULT 1 CHECK(generation>0),
 error_code VARCHAR(80),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(artwork_id,model,model_version,preprocessing_version)
);
CREATE TABLE embedding_outbox (
 event_id UUID PRIMARY KEY,
 job_id UUID NOT NULL REFERENCES embedding_job(id),
 generation INTEGER NOT NULL,
 payload TEXT NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0,
 failed BOOLEAN NOT NULL DEFAULT FALSE,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 published_at TIMESTAMPTZ
);
CREATE INDEX ix_embedding_outbox_pending ON embedding_outbox(next_attempt_at) WHERE published_at IS NULL AND failed=FALSE;
CREATE TABLE artwork_embedding (
 id UUID PRIMARY KEY,
 job_id UUID NOT NULL UNIQUE REFERENCES embedding_job(id),
 artwork_id UUID NOT NULL REFERENCES artwork(id),
 model VARCHAR(100) NOT NULL,
 model_version VARCHAR(100) NOT NULL,
 preprocessing_version VARCHAR(120) NOT NULL,
 embedding vector(768) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(artwork_id,model,model_version,preprocessing_version)
);
