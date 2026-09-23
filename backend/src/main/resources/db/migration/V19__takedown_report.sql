CREATE TABLE takedown_report (
 id UUID PRIMARY KEY,
 owner_id UUID NOT NULL REFERENCES app_user(id),
 detection_id UUID NOT NULL UNIQUE REFERENCES detection(id),
 evidence_id UUID NOT NULL REFERENCES evidence_snapshot(id),
 draft JSONB NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','SUBMITTED','RESOLVED','REJECTED','WITHDRAWN')),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 external_reference VARCHAR(200),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK(status NOT IN ('SUBMITTED','RESOLVED','REJECTED') OR length(trim(external_reference))>0 AND external_reference IS NOT NULL)
);
CREATE FUNCTION guard_takedown_report() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Report history cannot be deleted'; END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.status<>'DRAFT' OR NEW.version<>0 OR NOT EXISTS(
   SELECT 1 FROM evidence_snapshot e JOIN detection d ON d.id=e.detection_id
    JOIN artwork a ON a.id=d.artwork_id
   WHERE e.id=NEW.evidence_id AND d.id=NEW.detection_id AND a.user_id=NEW.owner_id AND d.review_status='CONFIRMED'
  ) THEN RAISE EXCEPTION 'Invalid report source'; END IF;
 ELSE
  IF (NEW.id,NEW.owner_id,NEW.detection_id,NEW.evidence_id,NEW.draft,NEW.created_at)
   IS DISTINCT FROM (OLD.id,OLD.owner_id,OLD.detection_id,OLD.evidence_id,OLD.draft,OLD.created_at)
   OR NEW.version<>OLD.version+1 OR NOT (
    OLD.status='DRAFT' AND NEW.status IN ('SUBMITTED','WITHDRAWN') OR
    OLD.status='SUBMITTED' AND NEW.status IN ('RESOLVED','REJECTED','WITHDRAWN'))
   THEN RAISE EXCEPTION 'Invalid report transition'; END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER takedown_report_guard BEFORE INSERT OR UPDATE OR DELETE ON takedown_report
 FOR EACH ROW EXECUTE FUNCTION guard_takedown_report();
CREATE FUNCTION audit_takedown_report() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO operational_audit(subject_user_id,resource_type,resource_id,old_state,new_state)
 VALUES(NEW.owner_id,'TAKEDOWN_REPORT',NEW.id,
  CASE WHEN TG_OP='INSERT' THEN '{}'::jsonb ELSE jsonb_build_object('status',OLD.status,'version',OLD.version) END,
  jsonb_build_object('status',NEW.status,'version',NEW.version));
 RETURN NEW;
END;
$$;
CREATE TRIGGER takedown_report_audit AFTER INSERT OR UPDATE ON takedown_report
 FOR EACH ROW EXECUTE FUNCTION audit_takedown_report();
