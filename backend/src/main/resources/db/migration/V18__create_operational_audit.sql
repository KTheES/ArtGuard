-- Audit contains state transitions only, never billing payloads, secrets, or email addresses.
CREATE TABLE operational_audit (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 subject_user_id UUID NOT NULL,
 resource_type VARCHAR(30) NOT NULL,
 resource_id UUID NOT NULL,
 old_state JSONB,
 new_state JSONB NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX ix_operational_audit_subject ON operational_audit(subject_user_id,recorded_at DESC);

CREATE FUNCTION audit_subscription_transition() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(OLD.plan_code,OLD.status,OLD.cancel_at_period_end) IS DISTINCT FROM ROW(NEW.plan_code,NEW.status,NEW.cancel_at_period_end) THEN
  INSERT INTO operational_audit(subject_user_id,resource_type,resource_id,old_state,new_state)
  VALUES(NEW.user_id,'SUBSCRIPTION',NEW.user_id,
   jsonb_build_object('plan',OLD.plan_code,'status',OLD.status,'cancelAtPeriodEnd',OLD.cancel_at_period_end),
   jsonb_build_object('plan',NEW.plan_code,'status',NEW.status,'cancelAtPeriodEnd',NEW.cancel_at_period_end));
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER subscription_audit AFTER UPDATE ON user_subscription
 FOR EACH ROW EXECUTE FUNCTION audit_subscription_transition();

CREATE FUNCTION audit_detection_review() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.review_status IS DISTINCT FROM NEW.review_status THEN
  INSERT INTO operational_audit(subject_user_id,resource_type,resource_id,old_state,new_state)
  SELECT a.user_id,'DETECTION_REVIEW',NEW.id,jsonb_build_object('status',OLD.review_status),
   jsonb_build_object('status',NEW.review_status) FROM artwork a WHERE a.id=NEW.artwork_id;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER detection_review_audit AFTER UPDATE ON detection
 FOR EACH ROW EXECUTE FUNCTION audit_detection_review();
CREATE TRIGGER operational_audit_immutable BEFORE UPDATE OR DELETE ON operational_audit
 FOR EACH ROW EXECUTE FUNCTION reject_evidence_mutation();
