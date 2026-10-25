package pl.mycomap.auth;

import jakarta.validation.constraints.*;
import java.util.Locale;
import java.util.UUID;
import pl.mycomap.security.SessionUser;
import pl.mycomap.user.AppUser;

public final class AuthDtos {
    private AuthDtos() {}
    public record RegisterRequest(
            @NotBlank(message = "Podaj nazwę użytkownika.")
            @Pattern(regexp = "[A-Za-z0-9_.-]{3,32}", message = "Użyj 3–32 liter bez polskich znaków, cyfr, kropek, myślników lub podkreśleń.") String username,
            @NotBlank(message = "Podaj adres e-mail.") @Email(message = "Podaj poprawny adres e-mail.")
            @Size(max = 254, message = "Adres e-mail jest zbyt długi.") String email,
            @NotBlank(message = "Podaj hasło.") @Size(min = 12, max = 72, message = "Hasło musi mieć od 12 do 72 znaków (maksymalnie 72 bajty UTF-8).") String password) {
        public RegisterRequest {
            username = username == null ? null : username.trim();
            email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        }
        @Override public String toString() { return "RegisterRequest[REDACTED]"; }
    }
    public record LoginRequest(
            @NotBlank(message = "Podaj nazwę użytkownika.") @Size(max = 32, message = "Nazwa jest zbyt długa.") String username,
            @NotBlank(message = "Podaj hasło.") @Size(max = 72, message = "Hasło jest zbyt długie.") String password) {
        public LoginRequest { username = username == null ? null : username.trim(); }
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }
    public record UserResponse(UUID id, String username, String email) {
        public static UserResponse from(AppUser user) { return new UserResponse(user.getId(), user.getUsername(), user.getEmail()); }
        public static UserResponse from(SessionUser user) { return new UserResponse(user.id(), user.username(), user.email()); }
    }
    public record CsrfResponse(String token, String headerName) {}
    public record VerificationRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "Nieprawidłowy link weryfikacyjny.") String token) {
        @Override public String toString() { return "VerificationRequest[REDACTED]"; }
    }
    public record ResendRequest(@NotBlank @Email(message = "Podaj poprawny adres e-mail.") @Size(max = 254) String email) {}
    public record MessageResponse(String message) {}
}
