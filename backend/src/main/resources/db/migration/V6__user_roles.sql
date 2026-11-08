ALTER TABLE app_users ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','ADMIN'));
ALTER TABLE app_users ADD COLUMN account_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE app_users ADD COLUMN auth_version INTEGER NOT NULL DEFAULT 0 CHECK (auth_version >= 0);
CREATE INDEX idx_users_role ON app_users(role);

CREATE SEQUENCE mushroom_species_id_seq OWNED BY mushroom_species.id;
SELECT setval('mushroom_species_id_seq', (SELECT max(id) FROM mushroom_species));
ALTER TABLE mushroom_species ALTER COLUMN id SET DEFAULT nextval('mushroom_species_id_seq');
CREATE UNIQUE INDEX uq_species_lower_name ON mushroom_species(lower(polish_name));
