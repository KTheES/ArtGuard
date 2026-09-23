ALTER TABLE detection ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE detection ADD COLUMN reviewed_at TIMESTAMPTZ;
CREATE FUNCTION bump_detection_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 NEW.version := OLD.version + 1;
 RETURN NEW;
END;
$$;
CREATE TRIGGER detection_version_before_update BEFORE UPDATE ON detection
FOR EACH ROW EXECUTE FUNCTION bump_detection_version();
CREATE INDEX ix_detection_review_filter ON detection(artwork_id,review_status,last_detected_at DESC,id);
