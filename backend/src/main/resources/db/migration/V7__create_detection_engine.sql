CREATE TABLE detection_run (
 id UUID PRIMARY KEY, artwork_id UUID NOT NULL REFERENCES artwork(id),
 artwork_embedding_id UUID NOT NULL REFERENCES artwork_embedding(id),
 model VARCHAR(100) NOT NULL, model_version VARCHAR(100) NOT NULL, preprocessing_version VARCHAR(120) NOT NULL,
 medium_threshold DOUBLE PRECISION NOT NULL, high_threshold DOUBLE PRECISION NOT NULL, critical_threshold DOUBLE PRECISION NOT NULL,
 result_limit INTEGER NOT NULL CHECK(result_limit BETWEEN 1 AND 500),
 matched_products INTEGER NOT NULL CHECK(matched_products>=0), truncated BOOLEAN NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK(medium_threshold>=0 AND medium_threshold<high_threshold AND high_threshold<critical_threshold AND critical_threshold<=1)
);
CREATE TABLE detection (
 id UUID PRIMARY KEY, artwork_id UUID NOT NULL REFERENCES artwork(id),
 product_id UUID NOT NULL REFERENCES product(id),
 artwork_embedding_id UUID NOT NULL REFERENCES artwork_embedding(id),
 product_embedding_id UUID NOT NULL REFERENCES product_image_embedding(id),
 last_run_id UUID NOT NULL REFERENCES detection_run(id),
 model VARCHAR(100) NOT NULL, model_version VARCHAR(100) NOT NULL, preprocessing_version VARCHAR(120) NOT NULL,
 similarity DOUBLE PRECISION NOT NULL CHECK(similarity>=0 AND similarity<=1),
 severity VARCHAR(20) NOT NULL CHECK(severity IN ('MEDIUM','HIGH','CRITICAL')),
 review_status VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK(review_status IN ('NEW','CONFIRMED','DISMISSED')),
 first_detected_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_detected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(artwork_id,product_id,model,model_version,preprocessing_version)
);
CREATE INDEX ix_detection_artwork_time ON detection(artwork_id,last_detected_at DESC,id);
CREATE INDEX ix_detection_run_artwork ON detection_run(artwork_id,created_at DESC);
