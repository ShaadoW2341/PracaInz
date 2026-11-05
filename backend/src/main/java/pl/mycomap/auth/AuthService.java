package pl.mycomap.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import pl.mycomap.common.ApiException;
import pl.mycomap.user.AppUser;
import pl.mycomap.user.UserRepository;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final EmailVerificationService verification;
    private final pl.mycomap.mushroommap.MapService maps;
    public AuthService(UserRepository users, PasswordEncoder encoder, EmailVerificationService verification, pl.mycomap.mushroommap.MapService maps) {
        this.users = users; this.encoder = encoder; this.verification = verification;
        this.maps = maps;
    }

    @Transactional
    public AuthDtos.UserResponse register(AuthDtos.RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) throw ApiException.invalidPassword();
        if (users.existsByUsernameIgnoreCaseOrEmailIgnoreCase(request.username(), request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", "Nazwa użytkownika lub e-mail są już zajęte.");
        }
        var user = users.saveAndFlush(new AppUser(request.username(), request.email(), encoder.encode(request.password())));
        maps.ensureDefault(user.getId());
        verification.send(user);
        return AuthDtos.UserResponse.from(user);
    }
}
