-- Existing tickets were issued with a fixed ten-minute TTL.
ALTER TABLE artwork_upload ADD COLUMN created_at TIMESTAMPTZ;
UPDATE artwork_upload SET created_at=expires_at-interval '10 minutes';
ALTER TABLE artwork_upload ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE artwork_upload ALTER COLUMN created_at SET NOT NULL;
CREATE INDEX ix_upload_owner_created ON artwork_upload(user_id,created_at DESC);
CREATE TABLE artwork_source_metadata (
 artwork_id UUID PRIMARY KEY REFERENCES artwork(id),
 sha256 CHAR(64) NOT NULL CHECK(sha256 ~ '^[0-9a-f]{64}$'),
 size_bytes BIGINT NOT NULL CHECK(size_bytes>0 AND size_bytes<=20971520),
 content_type VARCHAR(50) NOT NULL CHECK(content_type IN ('image/png','image/jpeg')),
 width INTEGER NOT NULL CHECK(width>0),height INTEGER NOT NULL CHECK(height>0),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER artwork_source_metadata_immutable BEFORE UPDATE OR DELETE ON artwork_source_metadata
 FOR EACH ROW EXECUTE FUNCTION reject_evidence_mutation();
