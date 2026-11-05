package pl.mycomap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.mycomap.security.SessionUser;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class MapsIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> database = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    SessionUser account() {
        UUID id = UUID.randomUUID(); String name = "map_" + id.toString().substring(0,8);
        jdbc.update("INSERT INTO app_users(id,username,email,password_hash) VALUES (?,?,?,?)", id, name, name+"@example.com", "test-only");
        return new SessionUser(id,name,name+"@example.com","test-only",true);
    }
    RequestPostProcessor as(SessionUser actor) { return user(actor); }
    String defaultMap(SessionUser actor) throws Exception {
        var result = mvc.perform(get("/api/maps").with(as(actor))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("items").get(0).get("id").asText();
    }
    String observation(String species, double lat, double lon) throws Exception {
        return json.writeValueAsString(Map.of("speciesId",Long.parseLong(species),"quantity",3,"observedOn","2026-10-01","description","Przy ścieżce", "latitude",lat,"longitude",lon));
    }
    String add(SessionUser actor, String map, String body) throws Exception {
        var result = mvc.perform(post("/api/maps/"+map+"/observations").with(as(actor)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test void defaultMapCrudAndCoordinatesSurviveDatabaseRoundTrip() throws Exception {
        var actor = account(); String map = defaultMap(actor);
        assertThat(defaultMap(actor)).isEqualTo(map);
        mvc.perform(delete("/api/maps/"+map).with(as(actor)).with(csrf())).andExpect(status().isConflict());
        String id = add(actor,map,observation("2",52.7,23.8));
        assertThat(jdbc.queryForObject("SELECT ST_X(location) FROM observations WHERE id=?",Double.class,UUID.fromString(id))).isEqualTo(23.8);
        mvc.perform(get("/api/observations/"+id).with(as(actor))).andExpect(status().isOk()).andExpect(jsonPath("$.latitude").value(52.7));
        mvc.perform(get("/api/maps/"+map+"/observations/geojson").param("bbox","23,52,24,53").with(as(actor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value(23.8));
        mvc.perform(patch("/api/observations/"+id).with(as(actor)).with(csrf()).contentType("application/json").content(observation("1",51.1,20.2))).andExpect(status().isOk());
        mvc.perform(delete("/api/observations/"+id).with(as(actor)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/observations/"+id).with(as(actor))).andExpect(status().isNotFound());
    }

    @Test void foreignIdsNeverExposeMapsOrObservations() throws Exception {
        var owner = account(); var stranger = account(); String map = defaultMap(owner);
        String id = add(owner,map,observation("1",52,23));
        for (String path : new String[]{"/api/maps/"+map,"/api/maps/"+map+"/observations","/api/maps/"+map+"/observations/geojson?bbox=22,51,24,53","/api/observations/"+id}) {
            mvc.perform(get(path).with(as(stranger))).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/maps/"+map+"/observations").with(as(stranger)).with(csrf()).contentType("application/json").content(observation("1",52,23))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/observations/"+id).with(as(stranger)).with(csrf()).contentType("application/json").content(observation("1",52,23))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/observations/"+id).with(as(stranger)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/maps/"+map).with(as(stranger)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/maps/"+map).with(as(stranger)).with(csrf()).contentType("application/json").content("{\"name\":\"obca\",\"description\":\"\"}")).andExpect(status().isNotFound());
    }

    @Test void filtersPaginationValidationAndMapDeletionWork() throws Exception {
        var actor = account(); String map = defaultMap(actor);
        add(actor,map,observation("1",52,23)); add(actor,map,observation("2",51,20));
        mvc.perform(get("/api/maps/"+map+"/observations").with(as(actor)).param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/maps/"+map+"/observations").with(as(actor)).param("speciesId","2")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/maps/"+map+"/observations").with(as(actor)).param("from","2026-10-02")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/maps/"+map+"/observations/geojson").with(as(actor)).param("bbox","NaN,0,1,1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/maps/not-a-uuid").with(as(actor))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/maps/"+map+"/observations").with(as(actor)).with(csrf()).contentType("application/json").content(observation("1",91,23))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/maps/"+map+"/observations").with(as(actor)).with(csrf()).contentType("application/json").content(observation("99999",52,23))).andExpect(status().isBadRequest());
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Warsaw")).toString();
        add(actor,map,observation("1",52,23).replace("2026-10-01",today));
        String tomorrow = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Warsaw")).plusDays(1).toString();
        mvc.perform(post("/api/maps/"+map+"/observations").with(as(actor)).with(csrf()).contentType("application/json").content(observation("1",52,23).replace("2026-10-01",tomorrow))).andExpect(status().isBadRequest());
        var created = mvc.perform(post("/api/maps").with(as(actor)).with(csrf()).contentType("application/json").content("{\"name\":\"Jesień\",\"description\":\"Wyprawy\"}")).andExpect(status().isCreated()).andReturn();
        String extra = json.readTree(created.getResponse().getContentAsString()).get("id").asText();
        String obs = add(actor,extra,observation("1",52,23));
        mvc.perform(delete("/api/maps/"+extra).with(as(actor)).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM observations WHERE id=?",Integer.class,UUID.fromString(obs))).isZero();
    }
}
