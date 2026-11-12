package pl.mycomap.sharing;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import pl.mycomap.common.ApiException;
import java.util.Map;
import java.util.UUID;

@Service
public class MapAccessService {
    public record Access(UUID mapId,boolean canEdit,String ownerUsername,MapPrecision precision) {
        public boolean coarse() { return !canEdit && precision==MapPrecision.COARSE; }
        public String locationSql() { return coarse() ? "mycomap_coarse_location(o.location)" : "o.location"; }
    }
    private final NamedParameterJdbcTemplate jdbc;
    public MapAccessService(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Access read(UUID actor,UUID map) {
        return jdbc.query("""
                SELECT m.id,m.owner_id,u.username,s.precision FROM mushroom_maps m
                JOIN app_users u ON u.id=m.owner_id
                LEFT JOIN map_shares s ON s.map_id=m.id AND s.shared_with_user_id=:actor
                WHERE m.id=:map AND (m.owner_id=:actor OR (s.id IS NOT NULL AND u.account_enabled AND u.email_verified))
                """,Map.of("actor",actor,"map",map),(rs,n)->{
            boolean owner=actor.equals(rs.getObject("owner_id",UUID.class));
            return new Access(map,owner,rs.getString("username"),owner ? MapPrecision.EXACT : MapPrecision.valueOf(rs.getString("precision")));
        }).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Nie znaleziono mapy lub cofnięto dostęp."));
    }
}
