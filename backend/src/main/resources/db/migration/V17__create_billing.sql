ALTER TABLE user_subscription
 ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('TRIAL','ACTIVE','PAST_DUE','CANCELED')),
 ADD COLUMN provider VARCHAR(20),
 ADD COLUMN provider_customer_id VARCHAR(100),
 ADD COLUMN provider_subscription_id VARCHAR(100),
 ADD COLUMN current_period_end TIMESTAMPTZ,
 ADD COLUMN cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN provider_event_created_at TIMESTAMPTZ,
 ADD CONSTRAINT ck_subscription_provider CHECK(provider IS NULL OR provider='STRIPE');

CREATE UNIQUE INDEX uk_subscription_provider_customer ON user_subscription(provider,provider_customer_id)
 WHERE provider_customer_id IS NOT NULL;
CREATE UNIQUE INDEX uk_subscription_provider_id ON user_subscription(provider,provider_subscription_id)
 WHERE provider_subscription_id IS NOT NULL;

CREATE TABLE billing_checkout_request (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id),
 plan_code VARCHAR(20) NOT NULL REFERENCES subscription_plan(code),
 idempotency_key VARCHAR(100) NOT NULL,
 provider VARCHAR(20) NOT NULL CHECK(provider='STRIPE'),
 provider_session_id VARCHAR(100),
 checkout_url VARCHAR(2048),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 completed_at TIMESTAMPTZ,
 UNIQUE(user_id,idempotency_key),
 UNIQUE(provider,provider_session_id)
);

CREATE TABLE billing_webhook_event (
 provider VARCHAR(20) NOT NULL CHECK(provider='STRIPE'),
 external_event_id VARCHAR(100) NOT NULL,
 event_type VARCHAR(100) NOT NULL,
 payload_sha256 VARCHAR(64) NOT NULL CHECK(payload_sha256 ~ '^[a-f0-9]{64}$'),
 outcome VARCHAR(20) NOT NULL CHECK(outcome IN ('PROCESSING','PROCESSED','IGNORED')),
 received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 processed_at TIMESTAMPTZ,
 PRIMARY KEY(provider,external_event_id)
);
