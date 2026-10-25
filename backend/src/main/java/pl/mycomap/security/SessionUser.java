package pl.mycomap.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import pl.mycomap.user.AppUser;

public record SessionUser(UUID id, String username, String email, String passwordHash, boolean emailVerified) implements UserDetails {
    public static SessionUser from(AppUser user) {
        return new SessionUser(user.getId(), user.getUsername(), user.getEmail(), user.getPasswordHash(), user.isEmailVerified());
    }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_USER")); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    // Nie umieszczaj hasha ani danych logowania w diagnostyce sesji.
    @Override public String toString() { return "SessionUser[" + id + "]"; }
}
