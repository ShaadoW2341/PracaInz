package pl.mycomap.common;

import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final HealthEndpoint health;

    public HealthController(HealthEndpoint health) {
        this.health = health;
    }

    @GetMapping("/api/health")
    public ResponseEntity<HealthResponse> health() {
        boolean ready = Status.UP.equals(health.health().getStatus());
        return ResponseEntity.status(ready ? 200 : 503)
                .body(new HealthResponse(ready ? "UP" : "DOWN", "MycoMap", 0));
    }

    public record HealthResponse(String status, String application, int stage) {}
}
