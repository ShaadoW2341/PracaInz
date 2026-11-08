package pl.mycomap.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import pl.mycomap.user.AppUser;
import pl.mycomap.user.UserRole;

public record SessionUser(UUID id, String username, String email, String passwordHash, boolean emailVerified,
                          UserRole role, boolean accountEnabled, int authVersion) implements UserDetails {
    public SessionUser(UUID id, String username, String email, String passwordHash, boolean emailVerified) {
        this(id,username,email,passwordHash,emailVerified,UserRole.USER,true,0);
    }
    public static SessionUser from(AppUser user) {
        return new SessionUser(user.getId(), user.getUsername(), user.getEmail(), user.getPasswordHash(), user.isEmailVerified(),user.getRole(),user.isAccountEnabled(),user.getAuthVersion());
    }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return role == UserRole.ADMIN ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),new SimpleGrantedAuthority("ROLE_USER")) : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    @Override public boolean isEnabled() { return accountEnabled; }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    // Nie umieszczaj hasha ani danych logowania w diagnostyce sesji.
    @Override public String toString() { return "SessionUser[" + id + "]"; }
}
