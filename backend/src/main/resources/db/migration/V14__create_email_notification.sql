CREATE TABLE notification_delivery (
 id UUID PRIMARY KEY,
 detection_id UUID NOT NULL REFERENCES detection(id),
 detection_run_id UUID NOT NULL REFERENCES detection_run(id),
 evidence_id UUID NOT NULL REFERENCES evidence_snapshot(id),
 severity VARCHAR(20) NOT NULL CHECK(severity IN ('HIGH','CRITICAL')),
 recipient VARCHAR(254) NOT NULL CHECK(recipient=lower(trim(recipient))),
 subject VARCHAR(200) NOT NULL,
 body TEXT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','SENDING','SENT','FAILED')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 locked_until TIMESTAMPTZ,
 error_code VARCHAR(80),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 sent_at TIMESTAMPTZ,
 UNIQUE(detection_id,severity)
);
CREATE INDEX ix_notification_pending ON notification_delivery(next_attempt_at,created_at)
 WHERE status='PENDING' OR status='SENDING';
