CREATE TABLE observation_photos (
    id UUID PRIMARY KEY,
    observation_id UUID NOT NULL REFERENCES observations(id) ON DELETE CASCADE,
    stored_filename VARCHAR(40) NOT NULL UNIQUE,
    content_type VARCHAR(30) NOT NULL CHECK (content_type = 'image/jpeg'),
    size_bytes INTEGER NOT NULL CHECK (size_bytes > 0),
    width INTEGER NOT NULL CHECK (width > 0 AND width <= 2048),
    height INTEGER NOT NULL CHECK (height > 0 AND height <= 2048),
    image_data BYTEA NOT NULL,
    thumbnail_data BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_photos_observation ON observation_photos(observation_id);
