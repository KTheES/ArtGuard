CREATE TABLE social_account_check (
 id UUID PRIMARY KEY,owner_id UUID NOT NULL REFERENCES app_user(id),profile_url VARCHAR(2048) NOT NULL,
 challenge VARCHAR(64) NOT NULL UNIQUE,status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','VERIFIED','REJECTED','REVOKED')),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 expires_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()+interval '24 hours',verified_until TIMESTAMPTZ,
 reviewer_id UUID REFERENCES app_user(id),reason VARCHAR(2000),
 CHECK(status NOT IN ('VERIFIED','REJECTED') OR reviewer_id IS NOT NULL AND reviewer_id<>owner_id AND reason IS NOT NULL AND length(trim(reason))>0),
 CHECK(status<>'VERIFIED' OR verified_until IS NOT NULL)
);
CREATE INDEX ix_social_owner ON social_account_check(owner_id,created_at DESC,id);
CREATE INDEX ix_social_pending ON social_account_check(expires_at) WHERE status='PENDING';
CREATE FUNCTION guard_social_check() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Social review history is immutable'; END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.status<>'PENDING' OR NEW.version<>0 OR NEW.reviewer_id IS NOT NULL OR NEW.verified_until IS NOT NULL OR NEW.reason IS NOT NULL
   OR NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.owner_id AND status='ACTIVE' AND email_verified_at IS NOT NULL)
   THEN RAISE EXCEPTION 'Invalid social check source'; END IF;
 ELSE
  IF (NEW.id,NEW.owner_id,NEW.profile_url,NEW.challenge,NEW.created_at,NEW.expires_at)
   IS DISTINCT FROM (OLD.id,OLD.owner_id,OLD.profile_url,OLD.challenge,OLD.created_at,OLD.expires_at) OR NEW.version<>OLD.version+1
   THEN RAISE EXCEPTION 'Invalid social check mutation'; END IF;
  IF NEW.status='REVOKED' AND OLD.status IN ('PENDING','VERIFIED') THEN
   IF (NEW.reviewer_id,NEW.reason,NEW.verified_until) IS DISTINCT FROM (OLD.reviewer_id,OLD.reason,OLD.verified_until)
    THEN RAISE EXCEPTION 'Review cannot change on revocation'; END IF;
  ELSIF OLD.status='PENDING' AND OLD.expires_at>clock_timestamp() AND NEW.status IN ('VERIFIED','REJECTED') THEN
   IF NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.reviewer_id AND status='ACTIVE' AND role='ROLE_ADMIN')
    THEN RAISE EXCEPTION 'Active administrator required'; END IF;
  ELSE RAISE EXCEPTION 'Invalid social check transition'; END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER social_check_guard BEFORE INSERT OR UPDATE OR DELETE ON social_account_check FOR EACH ROW EXECUTE FUNCTION guard_social_check();
CREATE FUNCTION audit_social_check() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO operational_audit(subject_user_id,resource_type,resource_id,old_state,new_state)
 VALUES(NEW.owner_id,'SOCIAL_ACCOUNT_CHECK',NEW.id,
  CASE WHEN TG_OP='INSERT' THEN '{}'::jsonb ELSE jsonb_build_object('status',OLD.status) END,
  jsonb_build_object('status',NEW.status,'actorId',CASE WHEN TG_OP='INSERT' OR NEW.status='REVOKED' THEN NEW.owner_id ELSE NEW.reviewer_id END));
 RETURN NEW;
END;
$$;
CREATE TRIGGER social_check_audit AFTER INSERT OR UPDATE ON social_account_check FOR EACH ROW EXECUTE FUNCTION audit_social_check();
