ALTER TABLE app_user ADD COLUMN email_verified_at TIMESTAMPTZ;
CREATE TABLE email_verification (
 user_id UUID PRIMARY KEY REFERENCES app_user(id),email VARCHAR(254) NOT NULL,
 token_hash CHAR(64) NOT NULL CHECK(token_hash ~ '^[0-9a-f]{64}$'),
 expires_at TIMESTAMPTZ NOT NULL,last_sent_at TIMESTAMPTZ NOT NULL
);
CREATE FUNCTION invalidate_email_verification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.email IS DISTINCT FROM OLD.email THEN
  NEW.email_verified_at=NULL;
  DELETE FROM email_verification WHERE user_id=NEW.id;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER app_user_email_verification_reset BEFORE UPDATE OF email ON app_user
 FOR EACH ROW EXECUTE FUNCTION invalidate_email_verification();
