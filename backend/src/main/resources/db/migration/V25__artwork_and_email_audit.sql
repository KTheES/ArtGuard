CREATE INDEX ix_audit_type_id ON operational_audit(resource_type,id DESC);
CREATE INDEX ix_audit_subject_id ON operational_audit(subject_user_id,id DESC);
CREATE FUNCTION audit_artwork_lifecycle() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE event_type TEXT;
BEGIN
 IF TG_OP='INSERT' THEN event_type='ARTWORK_CREATED';
 ELSIF OLD.deleted IS DISTINCT FROM NEW.deleted AND NEW.deleted THEN event_type='ARTWORK_DELETED';
 ELSIF (OLD.title,OLD.description,OLD.monitoring_enabled) IS DISTINCT FROM (NEW.title,NEW.description,NEW.monitoring_enabled) THEN event_type='ARTWORK_UPDATED';
 ELSE RETURN NEW;
 END IF;
 INSERT INTO operational_audit(subject_user_id,resource_type,resource_id,old_state,new_state)
 VALUES(NEW.user_id,event_type,NEW.id,
  CASE WHEN TG_OP='INSERT' THEN '{}'::jsonb ELSE jsonb_build_object('deleted',OLD.deleted,'monitoringEnabled',OLD.monitoring_enabled,'version',OLD.version) END,
  jsonb_build_object('deleted',NEW.deleted,'monitoringEnabled',NEW.monitoring_enabled,'version',NEW.version));
 RETURN NEW;
END;
$$;
CREATE TRIGGER artwork_lifecycle_audit AFTER INSERT OR UPDATE ON artwork FOR EACH ROW EXECUTE FUNCTION audit_artwork_lifecycle();
CREATE FUNCTION audit_email_verification_state() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (OLD.email_verified_at IS NULL) IS DISTINCT FROM (NEW.email_verified_at IS NULL) THEN
  INSERT INTO operational_audit(subject_user_id,resource_type,resource_id,old_state,new_state)
  VALUES(NEW.id,'EMAIL_VERIFICATION',NEW.id,jsonb_build_object('verified',OLD.email_verified_at IS NOT NULL),jsonb_build_object('verified',NEW.email_verified_at IS NOT NULL));
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER email_verification_audit AFTER UPDATE ON app_user FOR EACH ROW EXECUTE FUNCTION audit_email_verification_state();
