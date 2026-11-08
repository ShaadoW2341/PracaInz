package pl.mycomap.admin;

import jakarta.validation.constraints.*;
import pl.mycomap.user.UserRole;
import java.time.Instant;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}
    public record UserView(UUID id,String username,String email,UserRole role,boolean enabled,boolean emailVerified,Instant createdAt) {}
    public record UserInput(@NotNull UserRole role,@NotNull Boolean enabled) {}
    public record SpeciesView(long id,String polishName,String latinName,boolean isActive) {}
    public record SpeciesInput(@NotBlank @Size(max=100) String polishName,@Size(max=120) String latinName,@NotNull Boolean isActive) {}
}
