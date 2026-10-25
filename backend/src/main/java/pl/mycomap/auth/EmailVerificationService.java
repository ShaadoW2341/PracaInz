package pl.mycomap.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.mycomap.common.ApiException;
import pl.mycomap.user.AppUser;
import pl.mycomap.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class EmailVerificationService {
    private final UserRepository users;
    private final JavaMailSender mail;
    private final String publicUrl;
    private final String sender;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(UserRepository users, JavaMailSender mail,
            @Value("${app.public-url}") String publicUrl, @Value("${app.mail-from}") String sender) {
        this.users = users; this.mail = mail; this.publicUrl = publicUrl.replaceAll("/+$", ""); this.sender = sender;
    }

    public void send(AppUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.issueVerification(hash(token), Instant.now());
        var message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(user.getEmail());
        message.setSubject("MycoMap — potwierdź swój adres e-mail");
        message.setText("Witaj!\n\nPotwierdź adres e-mail swojego konta MycoMap:\n\n"
                + publicUrl + "/verify-email#token=" + token
                + "\n\nLink jest ważny przez 24 godziny i działa tylko raz.\nJeśli nie zakładałeś konta, zignoruj tę wiadomość.");
        try { mail.send(message); }
        catch (MailException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE", "Nie udało się wysłać wiadomości. Spróbuj ponownie później.");
        }
    }

    @Transactional
    public void verify(String token) {
        var user = users.findByVerificationHash(hash(token)).orElseThrow(this::invalidLink);
        if (user.getVerificationExpiresAt() == null || !user.getVerificationExpiresAt().isAfter(Instant.now())) throw invalidLink();
        user.verifyEmail();
    }

    @Transactional
    public void resend(String email) {
        users.findByEmailIgnoreCase(email.trim()).filter(user -> !user.isEmailVerified())
                .filter(user -> user.getVerificationSentAt() == null || user.getVerificationSentAt().isBefore(Instant.now().minusSeconds(60)))
                .ifPresent(this::send);
    }

    private ApiException invalidLink() {
        return new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_INVALID", "Link jest nieprawidłowy, wygasł lub został już wykorzystany. Poproś o nowy.");
    }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
