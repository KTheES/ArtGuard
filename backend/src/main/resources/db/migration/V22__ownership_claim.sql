CREATE TABLE ownership_claim (
 id UUID PRIMARY KEY,artwork_id UUID NOT NULL REFERENCES artwork(id),owner_id UUID NOT NULL REFERENCES app_user(id),
 publication_url VARCHAR(2048) NOT NULL,statement VARCHAR(4000) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','ACCEPTED','REJECTED')),
 review_reason VARCHAR(2000),reviewer_id UUID REFERENCES app_user(id),
 version BIGINT NOT NULL DEFAULT 0,submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),reviewed_at TIMESTAMPTZ,
 CHECK((status='PENDING' AND review_reason IS NULL AND reviewer_id IS NULL AND reviewed_at IS NULL AND version=0)
  OR(status<>'PENDING' AND review_reason IS NOT NULL AND length(trim(review_reason))>0 AND reviewer_id IS NOT NULL AND reviewer_id<>owner_id AND reviewed_at IS NOT NULL AND version=1))
);
CREATE UNIQUE INDEX ix_ownership_pending ON ownership_claim(artwork_id) WHERE status='PENDING';
CREATE INDEX ix_ownership_queue ON ownership_claim(status,submitted_at DESC,id);
CREATE FUNCTION guard_ownership_claim() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Ownership history is immutable'; END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.status<>'PENDING' OR NOT EXISTS(SELECT 1 FROM artwork a JOIN app_user u ON u.id=a.user_id
   WHERE a.id=NEW.artwork_id AND a.user_id=NEW.owner_id AND a.deleted=FALSE AND u.status='ACTIVE' AND u.email_verified_at IS NOT NULL)
   THEN RAISE EXCEPTION 'Invalid ownership source'; END IF;
 ELSE
  IF OLD.status<>'PENDING' OR NEW.status NOT IN ('ACCEPTED','REJECTED') OR
   (NEW.id,NEW.artwork_id,NEW.owner_id,NEW.publication_url,NEW.statement,NEW.submitted_at)
   IS DISTINCT FROM (OLD.id,OLD.artwork_id,OLD.owner_id,OLD.publication_url,OLD.statement,OLD.submitted_at)
   OR NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.reviewer_id AND status='ACTIVE' AND role='ROLE_ADMIN')
   THEN RAISE EXCEPTION 'Invalid ownership review'; END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER ownership_claim_guard BEFORE INSERT OR UPDATE OR DELETE ON ownership_claim FOR EACH ROW EXECUTE FUNCTION guard_ownership_claim();
CREATE FUNCTION audit_ownership_claim() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO operational_audit(subject_user_id,resource_type,resource_id,old_state,new_state)
 VALUES(NEW.owner_id,'OWNERSHIP_CLAIM',NEW.id,
  CASE WHEN TG_OP='INSERT' THEN '{}'::jsonb ELSE jsonb_build_object('status',OLD.status) END,
  jsonb_build_object('status',NEW.status,'actorId',CASE WHEN TG_OP='INSERT' THEN NEW.owner_id ELSE NEW.reviewer_id END));
 RETURN NEW;
END;
$$;
CREATE TRIGGER ownership_claim_audit AFTER INSERT OR UPDATE ON ownership_claim FOR EACH ROW EXECUTE FUNCTION audit_ownership_claim();
