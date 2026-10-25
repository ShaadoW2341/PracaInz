ALTER TABLE app_users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE app_users ADD COLUMN verification_hash VARCHAR(64);
ALTER TABLE app_users ADD COLUMN verification_expires_at TIMESTAMPTZ;
ALTER TABLE app_users ADD COLUMN verification_sent_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_verification_hash ON app_users (verification_hash) WHERE verification_hash IS NOT NULL;
