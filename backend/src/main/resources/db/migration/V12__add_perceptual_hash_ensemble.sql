ALTER TABLE artwork_embedding ADD COLUMN perceptual_hash_version VARCHAR(80), ADD COLUMN p_hash bit(64), ADD COLUMN d_hash bit(64);
ALTER TABLE product_image_embedding ADD COLUMN perceptual_hash_version VARCHAR(80), ADD COLUMN p_hash bit(64), ADD COLUMN d_hash bit(64);
ALTER TABLE product_image_region_embedding ADD COLUMN p_hash bit(64), ADD COLUMN d_hash bit(64);

ALTER TABLE detection ADD COLUMN embedding_similarity DOUBLE PRECISION;
ALTER TABLE detection ADD COLUMN phash_similarity DOUBLE PRECISION;
ALTER TABLE detection ADD COLUMN dhash_similarity DOUBLE PRECISION;
ALTER TABLE detection ADD COLUMN ensemble_version VARCHAR(80) NOT NULL DEFAULT 'embedding-only-v1';
UPDATE detection SET embedding_similarity=similarity;
ALTER TABLE detection ALTER COLUMN embedding_similarity SET NOT NULL;
ALTER TABLE detection ADD CHECK(embedding_similarity>=-1 AND embedding_similarity<=1);
ALTER TABLE detection ADD CHECK(phash_similarity IS NULL OR (phash_similarity>=0 AND phash_similarity<=1));
ALTER TABLE detection ADD CHECK(dhash_similarity IS NULL OR (dhash_similarity>=0 AND dhash_similarity<=1));

WITH reset AS (
 UPDATE embedding_job j SET status='QUEUED',generation=j.generation+1,error_code=NULL,updated_at=now()
 FROM artwork_embedding e WHERE e.job_id=j.id AND j.status='COMPLETED'
 RETURNING j.id,j.artwork_id,j.generation
), events AS (SELECT gen_random_uuid() event_id,gen_random_uuid() trace_id,id,artwork_id,generation FROM reset)
INSERT INTO embedding_outbox(event_id,job_id,generation,payload)
SELECT event_id,id,generation,jsonb_build_object('eventId',event_id,'eventType','EMBEDDING_REQUESTED','eventVersion',1,'occurredAt',now(),'traceId',trace_id,
 'payload',jsonb_build_object('jobId',id,'artworkId',artwork_id,'generation',generation))::text FROM events;

WITH reset AS (
 UPDATE product_embedding_job j SET status='QUEUED',generation=j.generation+1,error_code=NULL,updated_at=now()
 FROM product_image_embedding e WHERE e.job_id=j.id AND j.status='COMPLETED'
 RETURNING j.id,j.image_id,j.generation
), events AS (SELECT gen_random_uuid() event_id,gen_random_uuid() trace_id,id,image_id,generation FROM reset)
INSERT INTO product_embedding_outbox(event_id,job_id,generation,payload)
SELECT event_id,id,generation,jsonb_build_object('eventId',event_id,'eventType','PRODUCT_EMBEDDING_REQUESTED','eventVersion',1,'occurredAt',now(),'traceId',trace_id,
 'payload',jsonb_build_object('jobId',id,'imageId',image_id,'generation',generation))::text FROM events;
