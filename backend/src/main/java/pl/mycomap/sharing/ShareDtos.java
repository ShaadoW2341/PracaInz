package pl.mycomap.sharing;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ShareDtos {
    private ShareDtos() {}
    public record UserMatch(UUID id,String username) {}
    public record Share(UUID id,String username,MapPrecision precision,Instant createdAt) {}
    public record ShareInput(@NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{3,32}") String username,MapPrecision precision,boolean exactConfirmed) {
        public ShareInput { username=username==null ? null : username.trim(); precision=precision==null ? MapPrecision.COARSE : precision; }
    }
    public record PrecisionInput(@NotNull MapPrecision precision,boolean exactConfirmed) {}
}
