CREATE TABLE detection_job (
 id UUID PRIMARY KEY, artwork_id UUID NOT NULL REFERENCES artwork(id), source_key VARCHAR(150) NOT NULL UNIQUE,
 automatic BOOLEAN NOT NULL, result_limit INTEGER NOT NULL CHECK(result_limit BETWEEN 1 AND 500),
 status VARCHAR(20) NOT NULL CHECK(status IN ('QUEUED','COMPLETED','FAILED','CANCELED')),
 generation INTEGER NOT NULL DEFAULT 1 CHECK(generation>0), error_code VARCHAR(80),
 run_id UUID REFERENCES detection_run(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_detection_job_artwork ON detection_job(artwork_id,created_at DESC);
CREATE TABLE detection_outbox (
 event_id UUID PRIMARY KEY, job_id UUID NOT NULL REFERENCES detection_job(id), generation INTEGER NOT NULL,
 payload TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, failed BOOLEAN NOT NULL DEFAULT FALSE,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ
);
CREATE INDEX ix_detection_outbox_pending ON detection_outbox(next_attempt_at) WHERE published_at IS NULL AND failed=FALSE;
CREATE TABLE detection_signal (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), source_type VARCHAR(20) NOT NULL CHECK(source_type IN ('ARTWORK','PRODUCT')),
 embedding_id UUID NOT NULL, cursor_artwork_id UUID, completed BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(source_type,embedding_id)
);
CREATE INDEX ix_detection_signal_pending ON detection_signal(created_at) WHERE completed=FALSE;
CREATE FUNCTION record_detection_signal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO detection_signal(source_type,embedding_id)
 VALUES(CASE WHEN TG_TABLE_NAME='artwork_embedding' THEN 'ARTWORK' ELSE 'PRODUCT' END,NEW.id)
 ON CONFLICT(source_type,embedding_id) DO NOTHING;
 RETURN NEW;
END;
$$;
CREATE TRIGGER artwork_embedding_signal AFTER INSERT ON artwork_embedding FOR EACH ROW EXECUTE FUNCTION record_detection_signal();
CREATE TRIGGER product_embedding_signal AFTER INSERT ON product_image_embedding FOR EACH ROW EXECUTE FUNCTION record_detection_signal();
