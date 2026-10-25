package pl.mycomap.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppUser> findByVerificationHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppUser> findByEmailIgnoreCase(String email);
}
