CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_app_users_username ON app_users (lower(username));
CREATE UNIQUE INDEX uq_app_users_email ON app_users (lower(email));
