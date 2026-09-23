INSERT INTO marketplace(id,code,name,base_url,enabled)
VALUES('00000000-0000-0000-0000-000000000012','ALIEXPRESS','AliExpress','https://www.aliexpress.com',TRUE);
ALTER TABLE product_image DROP CONSTRAINT product_image_source_type_check;
ALTER TABLE product_image ADD CONSTRAINT product_image_source_type_check CHECK(source_type IN ('MOCK_RESOURCE','STORED_PNG'));
ALTER TABLE product_image ADD COLUMN size_bytes INTEGER NOT NULL DEFAULT 0 CHECK(size_bytes BETWEEN 0 AND 20971520);
ALTER TABLE product_image ADD CONSTRAINT product_image_stored_size CHECK(source_type!='STORED_PNG' OR size_bytes>0);
