CREATE TABLE monitoring_scan_cycle (
 id UUID PRIMARY KEY,
 window_start TIMESTAMPTZ NOT NULL UNIQUE,
 status VARCHAR(20) NOT NULL DEFAULT 'RUNNING' CHECK(status IN ('RUNNING','COMPLETED')),
 cursor_artwork_id UUID,
 scanned_artworks INTEGER NOT NULL DEFAULT 0 CHECK(scanned_artworks>=0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 completed_at TIMESTAMPTZ
);
CREATE INDEX ix_monitoring_cycle_running ON monitoring_scan_cycle(window_start,id) WHERE status='RUNNING';
