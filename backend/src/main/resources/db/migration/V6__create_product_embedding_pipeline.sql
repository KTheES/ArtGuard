CREATE TABLE product_embedding_job (
 id UUID PRIMARY KEY, image_id UUID NOT NULL REFERENCES product_image(id),
 image_hash VARCHAR(64) NOT NULL CHECK(image_hash ~ '^[a-f0-9]{64}$'),
 model VARCHAR(100) NOT NULL, model_version VARCHAR(100) NOT NULL, preprocessing_version VARCHAR(120) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('QUEUED','COMPLETED','FAILED','CANCELED')),
 generation INTEGER NOT NULL DEFAULT 1 CHECK(generation>0), error_code VARCHAR(80),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(image_id,image_hash,model,model_version,preprocessing_version)
);
CREATE TABLE product_embedding_outbox (
 event_id UUID PRIMARY KEY, job_id UUID NOT NULL REFERENCES product_embedding_job(id),
 generation INTEGER NOT NULL, payload TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0,
 failed BOOLEAN NOT NULL DEFAULT FALSE, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ
);
CREATE INDEX ix_product_embedding_outbox_pending ON product_embedding_outbox(next_attempt_at) WHERE published_at IS NULL AND failed=FALSE;
CREATE TABLE product_image_embedding (
 id UUID PRIMARY KEY, job_id UUID NOT NULL UNIQUE REFERENCES product_embedding_job(id),
 image_id UUID NOT NULL REFERENCES product_image(id), image_hash VARCHAR(64) NOT NULL,
 model VARCHAR(100) NOT NULL, model_version VARCHAR(100) NOT NULL, preprocessing_version VARCHAR(120) NOT NULL,
 embedding vector(768) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(image_id,image_hash,model,model_version,preprocessing_version)
);
-- Consumers of vectors must use this view to exclude superseded or inactive images.
CREATE VIEW current_product_image_embedding AS
 SELECT e.* FROM product_image_embedding e JOIN product_image i ON i.id=e.image_id AND i.image_hash=e.image_hash
 JOIN product p ON p.id=i.product_id JOIN marketplace m ON m.id=p.marketplace_id
 WHERE i.active=TRUE AND p.status='ACTIVE' AND m.enabled=TRUE;
