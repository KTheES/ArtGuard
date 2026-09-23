CREATE TABLE marketplace (
 id UUID PRIMARY KEY, code VARCHAR(30) NOT NULL UNIQUE,
 name VARCHAR(100) NOT NULL, base_url VARCHAR(2048) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE seller (
 id UUID PRIMARY KEY, marketplace_id UUID NOT NULL REFERENCES marketplace(id),
 external_seller_id VARCHAR(200) NOT NULL, seller_name VARCHAR(200) NOT NULL,
 seller_url VARCHAR(2048) NOT NULL, first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(marketplace_id,external_seller_id), UNIQUE(id,marketplace_id)
);
CREATE TABLE product (
 id UUID PRIMARY KEY, marketplace_id UUID NOT NULL REFERENCES marketplace(id),
 seller_id UUID NOT NULL, external_product_id VARCHAR(200) NOT NULL,
 title VARCHAR(500) NOT NULL, product_url VARCHAR(2048) NOT NULL,
 price NUMERIC(12,2) NOT NULL CHECK(price>=0), currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 status VARCHAR(20) NOT NULL CHECK(status IN ('ACTIVE','REMOVED')),
 first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(marketplace_id,external_product_id),
 FOREIGN KEY(seller_id,marketplace_id) REFERENCES seller(id,marketplace_id)
);
CREATE INDEX ix_product_catalog ON product(marketplace_id,last_seen_at DESC,id);
CREATE TABLE product_image (
 id UUID PRIMARY KEY, product_id UUID NOT NULL REFERENCES product(id),
 external_image_id VARCHAR(200) NOT NULL, original_url VARCHAR(2048) NOT NULL,
 source_type VARCHAR(30) NOT NULL CHECK(source_type IN ('MOCK_RESOURCE')),
 source_key VARCHAR(300) NOT NULL, storage_key VARCHAR(300),
 width INTEGER NOT NULL CHECK(width>0 AND width<=8192),
 height INTEGER NOT NULL CHECK(height>0 AND height<=8192),
 image_hash VARCHAR(64) NOT NULL CHECK(image_hash ~ '^[a-f0-9]{64}$'),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(product_id,external_image_id)
);
CREATE INDEX ix_product_image_active ON product_image(product_id) WHERE active=TRUE;
INSERT INTO marketplace(id,code,name,base_url,enabled)
VALUES('00000000-0000-0000-0000-000000000006','MOCK','Mock Marketplace','https://mock.artworkguard.invalid',TRUE);
