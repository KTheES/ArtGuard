-- Run with a read-only operational DB identity. No payloads or secrets are returned.
SELECT 'embedding' AS queue,count(*) FILTER (WHERE published_at IS NULL AND NOT failed) pending,
 count(*) FILTER (WHERE failed) failed,min(created_at) FILTER (WHERE published_at IS NULL AND NOT failed) oldest_pending
FROM embedding_outbox
UNION ALL
SELECT 'product_embedding',count(*) FILTER (WHERE published_at IS NULL AND NOT failed),
 count(*) FILTER (WHERE failed),min(created_at) FILTER (WHERE published_at IS NULL AND NOT failed)
FROM product_embedding_outbox
UNION ALL
SELECT 'detection',count(*) FILTER (WHERE published_at IS NULL AND NOT failed),
 count(*) FILTER (WHERE failed),min(created_at) FILTER (WHERE published_at IS NULL AND NOT failed)
FROM detection_outbox;
SELECT status,count(*),min(created_at) oldest FROM notification_delivery GROUP BY status;
SELECT outcome,count(*),max(received_at) last_received FROM billing_webhook_event GROUP BY outcome;
