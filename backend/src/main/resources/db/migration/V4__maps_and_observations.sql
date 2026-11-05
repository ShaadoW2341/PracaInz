CREATE TABLE mushroom_species (
    id BIGINT PRIMARY KEY,
    polish_name VARCHAR(100) NOT NULL UNIQUE,
    latin_name VARCHAR(120),
    is_active BOOLEAN NOT NULL DEFAULT true
);
INSERT INTO mushroom_species(id,polish_name,latin_name) VALUES
 (1,'Nieznany',NULL),(2,'Borowik szlachetny','Boletus edulis'),
 (3,'Podgrzybek brunatny','Imleria badia'),(4,'Koźlarz babka','Leccinum scabrum'),
 (5,'Koźlarz czerwony','Leccinum aurantiacum'),(6,'Maślak zwyczajny','Suillus luteus'),
 (7,'Maślak pstry','Suillus variegatus'),(8,'Pieprznik jadalny','Cantharellus cibarius'),
 (9,'Mleczaj rydz','Lactarius deliciosus'),(10,'Czubajka kania','Macrolepiota procera'),
 (11,'Muchomor czerwony','Amanita muscaria'),(12,'Muchomor sromotnikowy','Amanita phalloides'),
 (13,'Gołąbek zielonawy','Russula virescens'),(14,'Opieńka miodowa','Armillaria mellea');

CREATE TABLE mushroom_maps (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL CHECK (length(trim(name)) > 0),
    description VARCHAR(1000) NOT NULL DEFAULT '',
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_maps_owner ON mushroom_maps(owner_id);
CREATE UNIQUE INDEX uq_default_map ON mushroom_maps(owner_id) WHERE is_default;
INSERT INTO mushroom_maps(id,owner_id,name,is_default)
 SELECT gen_random_uuid(),id,'Moje znaleziska',true FROM app_users;

CREATE TABLE observations (
    id UUID PRIMARY KEY,
    map_id UUID NOT NULL REFERENCES mushroom_maps(id) ON DELETE CASCADE,
    species_id BIGINT NOT NULL REFERENCES mushroom_species(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0 AND quantity <= 100000),
    observed_on DATE NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    location geometry(Point,4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ST_X(location) BETWEEN -180 AND 180 AND ST_Y(location) BETWEEN -90 AND 90)
);
CREATE INDEX idx_observations_map_date ON observations(map_id,observed_on DESC);
CREATE INDEX idx_observations_species ON observations(species_id);
CREATE INDEX idx_observations_location ON observations USING GIST(location);
