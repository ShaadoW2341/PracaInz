package pl.mycomap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class HealthIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;

    @Test
    void realHttpPreservesSecurityStatusCodes() {
        assertThat(http.getForEntity("/api/maps", String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(http.postForEntity("/api/maps", null, String.class).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void healthAndMigrationAreReady() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        assertThat(jdbc.queryForObject("SELECT PostGIS_Version()", String.class)).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(1);
    }

    @Test
    void privateRoutesAndMutationsAreProtected() throws Exception {
        mvc.perform(get("/api/maps")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/maps")).andExpect(status().isForbidden());
    }
}
