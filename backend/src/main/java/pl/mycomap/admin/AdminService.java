package pl.mycomap.admin;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.mycomap.common.ApiException;
import pl.mycomap.mushroommap.MapDtos.Page;
import pl.mycomap.user.UserRole;
import java.util.UUID;
import static pl.mycomap.admin.AdminDtos.*;

@Service @Transactional
public class AdminService {
    private final JdbcTemplate jdbc;
    private static final RowMapper<UserView> USER_ROW = (rs,n) -> new UserView(rs.getObject("id",UUID.class),rs.getString("username"),rs.getString("email"),UserRole.valueOf(rs.getString("role")),rs.getBoolean("account_enabled"),rs.getBoolean("email_verified"),rs.getTimestamp("created_at").toInstant());
    private static final RowMapper<SpeciesView> SPECIES_ROW = (rs,n) -> new SpeciesView(rs.getLong("id"),rs.getString("polish_name"),rs.getString("latin_name"),rs.getBoolean("is_active"));
    public AdminService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    private void requireAdmin(UUID actor) {
        if (jdbc.queryForObject("SELECT count(*) FROM app_users WHERE id=? AND role='ADMIN' AND account_enabled AND email_verified",Integer.class,actor)!=1)
            throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Ta operacja wymaga roli administratora.");
    }
    public Page<UserView> users(UUID actor,String search,int page,int size) {
        requireAdmin(actor); String pattern=pattern(search,page,size);
        String where=" WHERE lower(username) LIKE ? OR lower(email) LIKE ?";
        long total=jdbc.queryForObject("SELECT count(*) FROM app_users"+where,Long.class,pattern,pattern);
        var items=jdbc.query("SELECT * FROM app_users"+where+" ORDER BY lower(username),id LIMIT ? OFFSET ?",USER_ROW,pattern,pattern,size,page*size);
        return new Page<>(items,total,page,size);
    }
    public UserView updateUser(UUID actor,UUID id,UserInput input) {
        // Jedna blokada transakcyjna serializuje zmiany ról i chroni ostatniego administratora.
        jdbc.execute("SELECT pg_advisory_xact_lock(726215)");
        requireAdmin(actor);
        var current=jdbc.query("SELECT * FROM app_users WHERE id=? FOR UPDATE",USER_ROW,id).stream().findFirst().orElseThrow(this::notFound);
        boolean activeAdmin=current.role()==UserRole.ADMIN && current.enabled() && current.emailVerified();
        if (activeAdmin && (input.role()!=UserRole.ADMIN || !input.enabled())
                && jdbc.queryForObject("SELECT count(*) FROM app_users WHERE role='ADMIN' AND account_enabled AND email_verified",Integer.class)<=1)
            throw new ApiException(HttpStatus.CONFLICT,"LAST_ADMIN","Nie można odebrać dostępu ostatniemu aktywnemu administratorowi.");
        if (current.role()!=input.role() || current.enabled()!=input.enabled())
            jdbc.update("UPDATE app_users SET role=?,account_enabled=?,auth_version=auth_version+1 WHERE id=?",input.role().name(),input.enabled(),id);
        return jdbc.queryForObject("SELECT * FROM app_users WHERE id=?",USER_ROW,id);
    }
    public Page<SpeciesView> species(UUID actor,String search,int page,int size) {
        requireAdmin(actor); String pattern=pattern(search,page,size);
        String where=" WHERE lower(polish_name) LIKE ? OR lower(coalesce(latin_name,'')) LIKE ?";
        long total=jdbc.queryForObject("SELECT count(*) FROM mushroom_species"+where,Long.class,pattern,pattern);
        var items=jdbc.query("SELECT * FROM mushroom_species"+where+" ORDER BY (id=1) DESC,lower(polish_name),id LIMIT ? OFFSET ?",SPECIES_ROW,pattern,pattern,size,page*size);
        return new Page<>(items,total,page,size);
    }
    public SpeciesView saveSpecies(UUID actor,Long id,SpeciesInput input) {
        requireAdmin(actor);
        if (id!=null && id==1) throw new ApiException(HttpStatus.CONFLICT,"SYSTEM_SPECIES","Wpis „Nieznany” jest stałym elementem słownika.");
        String name=input.polishName().trim();
        if (name.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Podaj nazwę gatunku.");
        String latin=input.latinName()==null || input.latinName().isBlank() ? null : input.latinName().trim();
        try {
            if (id==null) id=jdbc.queryForObject("INSERT INTO mushroom_species(polish_name,latin_name,is_active) VALUES (?,?,?) RETURNING id",Long.class,name,latin,input.isActive());
            else if (jdbc.update("UPDATE mushroom_species SET polish_name=?,latin_name=?,is_active=? WHERE id=?",name,latin,input.isActive(),id)==0) throw notFound();
        } catch (DataIntegrityViolationException error) {
            throw new ApiException(HttpStatus.CONFLICT,"SPECIES_EXISTS","Gatunek o tej nazwie już istnieje.");
        }
        return jdbc.queryForObject("SELECT * FROM mushroom_species WHERE id=?",SPECIES_ROW,id);
    }
    private String pattern(String search,int page,int size) {
        if(page<0 || page>100000 || size<1 || size>100 || (search!=null && search.length()>100))
            throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Nieprawidłowe parametry wyszukiwania lub strony.");
        return "%"+(search==null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT))+"%";
    }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Nie znaleziono wpisu."); }
}
