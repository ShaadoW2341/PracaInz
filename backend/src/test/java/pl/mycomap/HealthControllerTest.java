package pl.mycomap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import pl.mycomap.common.HealthController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {
    @Test
    void unavailableDependencyReturns503WithoutInternalDetails() {
        HealthEndpoint endpoint = mock(HealthEndpoint.class);
        when(endpoint.health()).thenReturn(Health.down()
                .withDetail("database", "internal connection details").build());

        var response = new HealthController(endpoint).health();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isEqualTo(new HealthController.HealthResponse("DOWN", "MycoMap", 1));
    }
}
