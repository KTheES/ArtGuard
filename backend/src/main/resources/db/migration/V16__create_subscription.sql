CREATE TABLE subscription_plan (
 code VARCHAR(20) PRIMARY KEY,
 display_name VARCHAR(50) NOT NULL,
 artwork_limit INTEGER NOT NULL CHECK(artwork_limit BETWEEN 1 AND 10000),
 scan_interval INTERVAL NOT NULL CHECK(scan_interval>=interval '1 hour'),
 detection_result_limit INTEGER NOT NULL CHECK(detection_result_limit BETWEEN 1 AND 500),
 queue_priority SMALLINT NOT NULL CHECK(queue_priority BETWEEN 0 AND 2),
 advanced_detection BOOLEAN NOT NULL,
 email_alert BOOLEAN NOT NULL,
 evidence BOOLEAN NOT NULL,
 reports BOOLEAN NOT NULL,
 sort_order SMALLINT NOT NULL UNIQUE
);

INSERT INTO subscription_plan(code,display_name,artwork_limit,scan_interval,detection_result_limit,queue_priority,advanced_detection,email_alert,evidence,reports,sort_order) VALUES
 ('FREE','Free',3,interval '7 days',25,0,FALSE,FALSE,FALSE,FALSE,1),
 ('CREATOR','Creator',50,interval '1 day',100,0,FALSE,TRUE,TRUE,FALSE,2),
 ('PRO','Pro',500,interval '6 hours',250,1,TRUE,TRUE,TRUE,TRUE,3),
 ('BUSINESS','Business',5000,interval '1 hour',500,2,TRUE,TRUE,TRUE,TRUE,4);

CREATE TABLE user_subscription (
 user_id UUID PRIMARY KEY REFERENCES app_user(id),
 plan_code VARCHAR(20) NOT NULL REFERENCES subscription_plan(code),
 started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO user_subscription(user_id,plan_code,started_at,updated_at)
SELECT id,'FREE',created_at,now() FROM app_user;

CREATE FUNCTION provision_free_subscription() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO user_subscription(user_id,plan_code,started_at,updated_at)
 VALUES(NEW.id,'FREE',NEW.created_at,NEW.created_at);
 RETURN NEW;
END;
$$;
CREATE TRIGGER app_user_free_subscription AFTER INSERT ON app_user
 FOR EACH ROW EXECUTE FUNCTION provision_free_subscription();

ALTER TABLE detection_job ADD COLUMN queue_priority SMALLINT NOT NULL DEFAULT 0
 CHECK(queue_priority BETWEEN 0 AND 2);
CREATE INDEX ix_detection_job_priority ON detection_job(queue_priority DESC,created_at)
 WHERE status='QUEUED';
