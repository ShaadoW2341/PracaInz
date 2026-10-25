package pl.mycomap.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import pl.mycomap.security.SessionUser;
import pl.mycomap.common.ApiException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrfTokens;
    private final EmailVerificationService verification;

    public AuthController(AuthService service, AuthenticationManager authentication,
                          SecurityContextRepository contexts, CsrfTokenRepository csrfTokens, EmailVerificationService verification) {
        this.service = service; this.authentication = authentication; this.contexts = contexts; this.csrfTokens = csrfTokens;
        this.verification = verification;
    }
    @PostMapping("/register")
    ResponseEntity<AuthDtos.UserResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.status(201).body(service.register(request));
    }
    @PostMapping("/login")
    AuthDtos.UserResponse login(@Valid @RequestBody AuthDtos.LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        if (body.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new BadCredentialsException("Invalid credentials");
        var authenticated = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        if (!((SessionUser) authenticated.getPrincipal()).emailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Potwierdź adres e-mail, zanim się zalogujesz. Sprawdź skrzynkę lub poproś o nowy link.");
        }
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authenticated, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        csrfTokens.saveToken(null, request, response);
        return AuthDtos.UserResponse.from((SessionUser) authenticated.getPrincipal());
    }
    @GetMapping("/me")
    AuthDtos.UserResponse me(@AuthenticationPrincipal SessionUser user) { return AuthDtos.UserResponse.from(user); }
    @GetMapping("/csrf")
    AuthDtos.CsrfResponse csrf(CsrfToken token) { return new AuthDtos.CsrfResponse(token.getToken(), token.getHeaderName()); }
    @PostMapping("/verify-email")
    AuthDtos.MessageResponse verify(@Valid @RequestBody AuthDtos.VerificationRequest request) {
        verification.verify(request.token());
        return new AuthDtos.MessageResponse("Adres e-mail został potwierdzony. Możesz się zalogować.");
    }
    @PostMapping("/resend-verification")
    AuthDtos.MessageResponse resend(@Valid @RequestBody AuthDtos.ResendRequest request) {
        verification.resend(request.email());
        return new AuthDtos.MessageResponse("Jeśli konto wymaga potwierdzenia, wysłaliśmy link na podany adres. Kolejną wiadomość można wysłać po 60 sekundach.");
    }
}
