package pl.mycomap.mushroommap;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.mycomap.common.ApiException;
import java.time.LocalDate;
import java.util.*;
import static pl.mycomap.mushroommap.MapDtos.*;

@Service @Transactional
public class MapService {
    private final NamedParameterJdbcTemplate jdbc;
    public MapService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String MAP_SELECT = "SELECT m.*, (SELECT count(*) FROM observations o WHERE o.map_id=m.id) observation_count FROM mushroom_maps m ";
    private static final String OBS_SELECT = "SELECT o.*,s.polish_name,s.latin_name,ST_Y(o.location) lat,ST_X(o.location) lon FROM observations o JOIN mushroom_species s ON s.id=o.species_id ";
    private static final RowMapper<MapView> MAP_ROW = (rs,n) -> new MapView(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("description"),rs.getBoolean("is_default"),rs.getLong("observation_count"));
    private static final RowMapper<Observation> OBS_ROW = (rs,n) -> new Observation(rs.getObject("id",UUID.class),rs.getObject("map_id",UUID.class),rs.getLong("species_id"),rs.getString("polish_name"),rs.getString("latin_name"),rs.getInt("quantity"),rs.getObject("observed_on",LocalDate.class),rs.getString("description"),rs.getDouble("lat"),rs.getDouble("lon"));

    public void ensureDefault(UUID owner) {
        jdbc.update("INSERT INTO mushroom_maps(id,owner_id,name,is_default) VALUES (:id,:owner,'Moje znaleziska',true) ON CONFLICT(owner_id) WHERE is_default DO NOTHING",Map.of("id",UUID.randomUUID(),"owner",owner));
    }
    public Page<MapView> maps(UUID owner, int page, int size) {
        pagination(page,size); ensureDefault(owner);
        var params = Map.of("owner",owner,"limit",size,"offset",page*size);
        var items = jdbc.query(MAP_SELECT+"WHERE m.owner_id=:owner ORDER BY m.is_default DESC,m.created_at,m.id LIMIT :limit OFFSET :offset",params,MAP_ROW);
        long total = jdbc.queryForObject("SELECT count(*) FROM mushroom_maps WHERE owner_id=:owner",params,Long.class);
        return new Page<>(items,total,page,size);
    }
    public MapView map(UUID owner, UUID id) {
        return jdbc.query(MAP_SELECT+"WHERE m.id=:id AND m.owner_id=:owner FOR UPDATE OF m",Map.of("id",id,"owner",owner),MAP_ROW)
                .stream().findFirst().orElseThrow(this::notFound);
    }
    public MapView createMap(UUID owner, MapInput input) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO mushroom_maps(id,owner_id,name,description) VALUES (:id,:owner,:name,:description)",Map.of("id",id,"owner",owner,"name",name(input.name()),"description",text(input.description())));
        return map(owner,id);
    }
    public MapView updateMap(UUID owner, UUID id, MapInput input) {
        map(owner,id);
        jdbc.update("UPDATE mushroom_maps SET name=:name,description=:description,updated_at=now() WHERE id=:id",Map.of("id",id,"name",name(input.name()),"description",text(input.description())));
        return map(owner,id);
    }
    public void deleteMap(UUID owner, UUID id) {
        if (map(owner,id).isDefault()) throw new ApiException(HttpStatus.CONFLICT,"DEFAULT_MAP_PROTECTED","Domyślnej mapy nie można usunąć.");
        jdbc.update("DELETE FROM mushroom_maps WHERE id=:id",Map.of("id",id));
    }
    public List<Species> species() {
        return jdbc.query("SELECT * FROM mushroom_species WHERE is_active ORDER BY (id=1) DESC,polish_name",(rs,n)->new Species(rs.getLong("id"),rs.getString("polish_name"),rs.getString("latin_name")));
    }
    public Observation observation(UUID owner, UUID id) {
        return jdbc.query(OBS_SELECT+"JOIN mushroom_maps m ON m.id=o.map_id WHERE o.id=:id AND m.owner_id=:owner",Map.of("id",id,"owner",owner),OBS_ROW)
                .stream().findFirst().orElseThrow(this::notFound);
    }
    public Observation saveObservation(UUID owner, UUID mapId, UUID id, ObservationInput input) {
        map(owner,mapId);
        if (!Double.isFinite(input.latitude()) || !Double.isFinite(input.longitude())) throw invalid("Nieprawidłowe współrzędne.");
        if (jdbc.queryForObject("SELECT count(*) FROM mushroom_species WHERE id=:id AND is_active",Map.of("id",input.speciesId()),Integer.class) != 1) throw invalid("Wybierz gatunek ze słownika.");
        var params = new MapSqlParameterSource().addValue("map",mapId).addValue("species",input.speciesId()).addValue("quantity",input.quantity())
                .addValue("date",input.observedOn()).addValue("description",text(input.description())).addValue("lat",input.latitude()).addValue("lon",input.longitude());
        if (id == null) {
            id = UUID.randomUUID(); params.addValue("id",id);
            jdbc.update("INSERT INTO observations(id,map_id,species_id,quantity,observed_on,description,location) VALUES (:id,:map,:species,:quantity,:date,:description,ST_SetSRID(ST_MakePoint(:lon,:lat),4326))",params);
        } else {
            if (!observation(owner,id).mapId().equals(mapId)) throw notFound();
            params.addValue("id",id);
            jdbc.update("UPDATE observations SET species_id=:species,quantity=:quantity,observed_on=:date,description=:description,location=ST_SetSRID(ST_MakePoint(:lon,:lat),4326),updated_at=now() WHERE id=:id AND map_id=:map",params);
        }
        return observation(owner,id);
    }
    public Observation updateObservation(UUID owner, UUID id, ObservationInput input) {
        return saveObservation(owner,observation(owner,id).mapId(),id,input);
    }
    public void deleteObservation(UUID owner, UUID id) {
        var observation = observation(owner,id); map(owner,observation.mapId());
        jdbc.update("DELETE FROM observations WHERE id=:id",Map.of("id",id));
    }
    public Page<Observation> observations(UUID owner, UUID map, Long species, LocalDate from, LocalDate to, String bbox, int page, int size) {
        map(owner,map); pagination(page,size);
        var params = new MapSqlParameterSource("map",map).addValue("limit",size).addValue("offset",page*size);
        String where = filter(params,species,from,to,bbox);
        long total = jdbc.queryForObject("SELECT count(*) FROM observations o "+where,params,Long.class);
        var items = jdbc.query(OBS_SELECT+where+" ORDER BY o.observed_on DESC,o.created_at DESC,o.id LIMIT :limit OFFSET :offset",params,OBS_ROW);
        return new Page<>(items,total,page,size);
    }
    public GeoJson geoJson(UUID owner, UUID map, Long species, LocalDate from, LocalDate to, String bbox) {
        map(owner,map);
        if (bbox == null || bbox.isBlank()) throw invalid("Wskaż obszar mapy.");
        var params = new MapSqlParameterSource("map",map);
        var rows = jdbc.query(OBS_SELECT+filter(params,species,from,to,bbox)+" ORDER BY o.id LIMIT 1001",params,OBS_ROW);
        var features = rows.stream().limit(1000).map(o -> new Feature("Feature",o.id(),new Geometry("Point",List.of(o.longitude(),o.latitude())),Map.<String,Object>of("id",o.id(),"speciesName",o.speciesName(),"quantity",o.quantity(),"observedOn",o.observedOn()))).toList();
        return new GeoJson("FeatureCollection",features,rows.size()>1000,1000);
    }
    private String filter(MapSqlParameterSource params, Long species, LocalDate from, LocalDate to, String bbox) {
        if (from != null && to != null && from.isAfter(to)) throw invalid("Początek zakresu dat musi poprzedzać koniec.");
        String sql = "WHERE o.map_id=:map";
        if (species != null) { sql+=" AND o.species_id=:species"; params.addValue("species",species); }
        if (from != null) { sql+=" AND o.observed_on>=:from"; params.addValue("from",from); }
        if (to != null) { sql+=" AND o.observed_on<=:to"; params.addValue("to",to); }
        if (bbox != null && !bbox.isBlank()) {
            double[] b;
            try { b = Arrays.stream(bbox.split(",",-1)).mapToDouble(Double::parseDouble).toArray(); }
            catch (NumberFormatException e) { throw invalid("Nieprawidłowy obszar mapy."); }
            if (b.length != 4 || Arrays.stream(b).anyMatch(v -> !Double.isFinite(v)) || b[0]<-180 || b[2]>180 || b[1]<-90 || b[3]>90 || b[0]>=b[2] || b[1]>=b[3] || b[2]-b[0]>120 || b[3]-b[1]>80) throw invalid("Nieprawidłowy lub zbyt duży obszar. Przybliż mapę.");
            params.addValue("west",b[0]).addValue("south",b[1]).addValue("east",b[2]).addValue("north",b[3]);
            sql+=" AND o.location && ST_MakeEnvelope(:west,:south,:east,:north,4326)";
        }
        return sql;
    }
    private void pagination(int page,int size) { if (page<0 || page>100000 || size<1 || size>100) throw invalid("Nieprawidłowy numer lub rozmiar strony."); }
    private String name(String name) { if (name.trim().isEmpty()) throw invalid("Podaj nazwę mapy."); return name.trim(); }
    private String text(String value) { return value == null ? "" : value.trim(); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Nie znaleziono zasobu."); }
    private ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message); }
}
