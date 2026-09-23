CREATE TABLE artwork_upload (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id),
 object_key VARCHAR(300) NOT NULL UNIQUE, content_type VARCHAR(50) NOT NULL,
 size_bytes BIGINT NOT NULL CHECK(size_bytes>0 AND size_bytes<=20971520),
 expires_at TIMESTAMPTZ NOT NULL, artwork_id UUID
);
CREATE INDEX ix_artwork_upload_expiry ON artwork_upload(expires_at);
CREATE TABLE artwork (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id),
 title VARCHAR(200) NOT NULL, description VARCHAR(5000) NOT NULL,
 original_key VARCHAR(300) NOT NULL UNIQUE, thumbnail_key VARCHAR(300) NOT NULL UNIQUE,
 width INTEGER NOT NULL CHECK(width>0), height INTEGER NOT NULL CHECK(height>0),
 monitoring_enabled BOOLEAN NOT NULL DEFAULT FALSE, deleted BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE artwork_upload ADD CONSTRAINT fk_upload_artwork FOREIGN KEY(artwork_id) REFERENCES artwork(id);
CREATE INDEX ix_artwork_owner_created ON artwork(user_id,created_at DESC,id) WHERE deleted=FALSE;
