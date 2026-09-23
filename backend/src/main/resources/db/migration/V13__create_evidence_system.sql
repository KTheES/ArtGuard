ALTER TABLE product_image ADD COLUMN storage_size_bytes INTEGER NOT NULL DEFAULT 0 CHECK(storage_size_bytes BETWEEN 0 AND 20971520);

CREATE TABLE evidence_snapshot (
 id UUID PRIMARY KEY,
 detection_id UUID NOT NULL REFERENCES detection(id),
 detection_run_id UUID NOT NULL REFERENCES detection_run(id),
 product_id UUID NOT NULL,
 image_id UUID NOT NULL,
 marketplace_code VARCHAR(30) NOT NULL,
 external_product_id VARCHAR(200) NOT NULL,
 product_title VARCHAR(500) NOT NULL,
 product_url VARCHAR(2048) NOT NULL,
 seller_external_id VARCHAR(200) NOT NULL,
 seller_name VARCHAR(200) NOT NULL,
 seller_url VARCHAR(2048) NOT NULL,
 price NUMERIC(12,2) NOT NULL CHECK(price>=0),
 currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 image_original_url VARCHAR(2048) NOT NULL,
 image_storage_key VARCHAR(300) NOT NULL,
 image_size_bytes INTEGER NOT NULL CHECK(image_size_bytes>0 AND image_size_bytes<=20971520),
 image_sha256 VARCHAR(64) NOT NULL CHECK(image_sha256 ~ '^[a-f0-9]{64}$'),
 snapshot_json TEXT NOT NULL CHECK(snapshot_json IS JSON),
 snapshot_sha256 VARCHAR(64) NOT NULL CHECK(snapshot_sha256 ~ '^[a-f0-9]{64}$'),
 screenshot_svg TEXT NOT NULL,
 screenshot_sha256 VARCHAR(64) NOT NULL CHECK(screenshot_sha256 ~ '^[a-f0-9]{64}$'),
 captured_at TIMESTAMPTZ NOT NULL,
 UNIQUE(detection_id,detection_run_id)
);
CREATE INDEX ix_evidence_detection_time ON evidence_snapshot(detection_id,captured_at DESC,id);

CREATE FUNCTION reject_evidence_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Evidence snapshots are immutable'; END;
$$;
CREATE TRIGGER evidence_snapshot_immutable BEFORE UPDATE OR DELETE ON evidence_snapshot FOR EACH ROW EXECUTE FUNCTION reject_evidence_mutation();

WITH reset AS (
 UPDATE product_embedding_job j SET status='QUEUED',generation=j.generation+1,error_code=NULL,updated_at=now()
 FROM product_image_embedding e WHERE e.job_id=j.id AND j.status='COMPLETED'
 RETURNING j.id,j.image_id,j.generation
), events AS (SELECT gen_random_uuid() event_id,gen_random_uuid() trace_id,id,image_id,generation FROM reset)
INSERT INTO product_embedding_outbox(event_id,job_id,generation,payload)
SELECT event_id,id,generation,jsonb_build_object('eventId',event_id,'eventType','PRODUCT_EMBEDDING_REQUESTED','eventVersion',1,'occurredAt',now(),'traceId',trace_id,
 'payload',jsonb_build_object('jobId',id,'imageId',image_id,'generation',generation))::text FROM events;
