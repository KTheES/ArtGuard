CREATE TABLE product_image_region_embedding (
 id UUID PRIMARY KEY,
 product_embedding_id UUID NOT NULL REFERENCES product_image_embedding(id) ON DELETE CASCADE,
 region_scheme VARCHAR(80) NOT NULL,
 region_key VARCHAR(40) NOT NULL,
 x DOUBLE PRECISION NOT NULL CHECK(x>=0 AND x<=1),
 y DOUBLE PRECISION NOT NULL CHECK(y>=0 AND y<=1),
 width DOUBLE PRECISION NOT NULL CHECK(width>0 AND width<=1),
 height DOUBLE PRECISION NOT NULL CHECK(height>0 AND height<=1),
 embedding vector(768) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK(x+width<=1.000001 AND y+height<=1.000001),
 UNIQUE(product_embedding_id,region_key)
);
ALTER TABLE detection ADD COLUMN product_region_embedding_id UUID REFERENCES product_image_region_embedding(id);
CREATE VIEW current_product_image_region_embedding AS
 SELECT r.* FROM product_image_region_embedding r
 JOIN current_product_image_embedding e ON e.id=r.product_embedding_id;

-- Requeue existing completed product embeddings once so they gain region vectors.
WITH reset AS (
 UPDATE product_embedding_job j SET status='QUEUED',generation=j.generation+1,error_code=NULL,updated_at=now()
 FROM product_image_embedding e
 WHERE e.job_id=j.id AND j.status='COMPLETED'
 RETURNING j.id,j.image_id,j.generation
), events AS (
 SELECT gen_random_uuid() AS event_id,gen_random_uuid() AS trace_id,id,image_id,generation FROM reset
)
INSERT INTO product_embedding_outbox(event_id,job_id,generation,payload)
SELECT event_id,id,generation,jsonb_build_object(
 'eventId',event_id,'eventType','PRODUCT_EMBEDDING_REQUESTED','eventVersion',1,'occurredAt',now(),'traceId',trace_id,
 'payload',jsonb_build_object('jobId',id,'imageId',image_id,'generation',generation)
)::text FROM events;
