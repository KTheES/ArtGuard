CREATE TABLE ownership_source_file (
 claim_id UUID PRIMARY KEY REFERENCES ownership_claim(id),format VARCHAR(20) NOT NULL CHECK(format IN ('PSD','PROCREATE')),
 sha256 CHAR(64) NOT NULL CHECK(sha256 ~ '^[0-9a-f]{64}$'),size_bytes BIGINT NOT NULL CHECK(size_bytes>0 AND size_bytes<=20971520),
 validation VARCHAR(80) NOT NULL,storage_key VARCHAR(300) NOT NULL UNIQUE,uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER ownership_source_file_immutable BEFORE UPDATE OR DELETE ON ownership_source_file FOR EACH ROW EXECUTE FUNCTION reject_evidence_mutation();
CREATE FUNCTION guard_ownership_source_file() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM ownership_claim c JOIN artwork a ON a.id=c.artwork_id JOIN app_user u ON u.id=c.owner_id
  WHERE c.id=NEW.claim_id AND c.status='PENDING' AND a.deleted=FALSE AND u.status='ACTIVE' AND u.email_verified_at IS NOT NULL)
  THEN RAISE EXCEPTION 'Pending owned claim required'; END IF;
 INSERT INTO operational_audit(subject_user_id,resource_type,resource_id,old_state,new_state)
 SELECT owner_id,'OWNERSHIP_SOURCE_FILE',NEW.claim_id,'{}'::jsonb,jsonb_build_object('sha256',NEW.sha256,'format',NEW.format,'sizeBytes',NEW.size_bytes)
 FROM ownership_claim WHERE id=NEW.claim_id;
 RETURN NEW;
END;
$$;
CREATE TRIGGER ownership_source_file_guard BEFORE INSERT ON ownership_source_file FOR EACH ROW EXECUTE FUNCTION guard_ownership_source_file();
