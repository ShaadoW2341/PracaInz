CREATE TABLE map_shares (
    id UUID PRIMARY KEY,
    map_id UUID NOT NULL REFERENCES mushroom_maps(id) ON DELETE CASCADE,
    shared_with_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    precision VARCHAR(10) NOT NULL DEFAULT 'COARSE' CHECK (precision IN ('COARSE','EXACT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(map_id,shared_with_user_id)
);
CREATE INDEX idx_shares_recipient ON map_shares(shared_with_user_id,map_id);

-- Środki komórek: ok. 1,1 km w pionie, szerokość dostosowana do szerokości geograficznej.
-- Ten sam punkt służy do wyświetlania oraz filtracji, aby bbox nie zdradzał dokładnej pozycji.
CREATE FUNCTION mycomap_coarse_location(point geometry) RETURNS geometry
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
    WITH latitude AS (
        SELECT -90.0 + (least(17999, greatest(0, floor((ST_Y(point)+90)*100)))+0.5)/100 AS lat
    ), grid AS (
        SELECT lat, ceil(36000 * greatest(cos(radians(lat)),0.01)) AS columns FROM latitude
    )
    SELECT ST_SetSRID(ST_MakePoint(
        -180.0 + (least(columns-1,greatest(0,floor((ST_X(point)+180)/(360.0/columns))))+0.5)*(360.0/columns),
        lat),4326) FROM grid;
$$;
