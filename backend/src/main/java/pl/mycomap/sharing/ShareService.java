package pl.mycomap.sharing;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.mycomap.common.ApiException;
import pl.mycomap.mushroommap.MapService;
import pl.mycomap.mushroommap.MapDtos.Page;
import java.util.*;
import static pl.mycomap.sharing.ShareDtos.*;

@Service @Transactional
public class ShareService {
    private final NamedParameterJdbcTemplate jdbc;
    private final MapService maps;
    private static final String SELECT="SELECT s.id,u.username,s.precision,s.created_at FROM map_shares s JOIN app_users u ON u.id=s.shared_with_user_id ";
    private static final RowMapper<Share> ROW=(rs,n)->new Share(rs.getObject("id",UUID.class),rs.getString("username"),MapPrecision.valueOf(rs.getString("precision")),rs.getTimestamp("created_at").toInstant());
    public ShareService(NamedParameterJdbcTemplate jdbc,MapService maps) { this.jdbc=jdbc; this.maps=maps; }

    public List<UserMatch> search(UUID actor,String query) {
        String prefix=query==null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if(prefix.length()<3 || prefix.length()>32) return List.of();
        prefix=prefix.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
        return jdbc.query("SELECT id,username FROM app_users WHERE id<>:actor AND account_enabled AND email_verified AND lower(username) LIKE :prefix ORDER BY lower(username),id LIMIT 10",Map.of("actor",actor,"prefix",prefix+"%"),
                (rs,n)->new UserMatch(rs.getObject("id",UUID.class),rs.getString("username")));
    }
    public Page<Share> list(UUID owner,UUID map,int page,int size) {
        maps.map(owner,map);
        if(page<0 || page>100000 || size<1 || size>100) throw invalid("Nieprawidłowa strona listy odbiorców.");
        var params=Map.of("map",map,"limit",size,"offset",page*size);
        long total=jdbc.queryForObject("SELECT count(*) FROM map_shares WHERE map_id=:map",params,Long.class);
        var items=jdbc.query(SELECT+"WHERE s.map_id=:map ORDER BY s.created_at DESC,s.id LIMIT :limit OFFSET :offset",params,ROW);
        return new Page<>(items,total,page,size);
    }
    public Share create(UUID owner,UUID map,ShareInput input) {
        maps.map(owner,map); confirm(input.precision(),input.exactConfirmed());
        UUID recipient=jdbc.query("SELECT id FROM app_users WHERE lower(username)=lower(:name) AND account_enabled AND email_verified",Map.of("name",input.username()),(rs,n)->rs.getObject(1,UUID.class))
                .stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","Nie znaleziono aktywnego użytkownika o tej nazwie."));
        if(owner.equals(recipient)) throw invalid("Nie musisz udostępniać mapy sobie.");
        var params=Map.of("map",map,"recipient",recipient);
        if(jdbc.queryForObject("SELECT count(*) FROM map_shares WHERE map_id=:map AND shared_with_user_id=:recipient",params,Integer.class)>0)
            throw new ApiException(HttpStatus.CONFLICT,"SHARE_EXISTS","Ta mapa jest już udostępniona temu użytkownikowi.");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO map_shares(id,map_id,shared_with_user_id,precision) VALUES (:id,:map,:recipient,:precision)",Map.of("id",id,"map",map,"recipient",recipient,"precision",input.precision().name()));
        return find(map,id);
    }
    public Share update(UUID owner,UUID map,UUID id,PrecisionInput input) {
        maps.map(owner,map); find(map,id); confirm(input.precision(),input.exactConfirmed());
        jdbc.update("UPDATE map_shares SET precision=:precision WHERE id=:id AND map_id=:map",Map.of("precision",input.precision().name(),"id",id,"map",map));
        return find(map,id);
    }
    public void revoke(UUID owner,UUID map,UUID id) {
        maps.map(owner,map);
        if(jdbc.update("DELETE FROM map_shares WHERE id=:id AND map_id=:map",Map.of("id",id,"map",map))==0) throw notFound();
    }
    private Share find(UUID map,UUID id) {
        return jdbc.query(SELECT+"WHERE s.id=:id AND s.map_id=:map",Map.of("id",id,"map",map),ROW).stream().findFirst().orElseThrow(this::notFound);
    }
    private void confirm(MapPrecision precision,boolean confirmed) {
        if(precision==MapPrecision.EXACT && !confirmed) throw new ApiException(HttpStatus.BAD_REQUEST,"EXACT_CONFIRMATION_REQUIRED","Potwierdź świadome udostępnienie dokładnych współrzędnych.");
    }
    private ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Nie znaleziono udostępnienia."); }
}
