package pl.mycomap.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id private UUID id;
    @Column(nullable = false, length = 32) private String username;
    @Column(nullable = false, length = 254) private String email;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "email_verified", nullable = false) private boolean emailVerified;
    @Column(name = "verification_hash", length = 64) private String verificationHash;
    @Column(name = "verification_expires_at") private Instant verificationExpiresAt;
    @Column(name = "verification_sent_at") private Instant verificationSentAt;

    protected AppUser() {}
    public AppUser(String username, String email, String passwordHash) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEmailVerified() { return emailVerified; }
    public Instant getVerificationExpiresAt() { return verificationExpiresAt; }
    public Instant getVerificationSentAt() { return verificationSentAt; }
    public void issueVerification(String hash, Instant now) {
        verificationHash = hash;
        verificationSentAt = now;
        verificationExpiresAt = now.plusSeconds(24 * 60 * 60);
    }
    public void verifyEmail() {
        emailVerified = true;
        verificationHash = null;
        verificationExpiresAt = null;
    }
}
