package pl.mycomap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.mycomap.security.SessionUser;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PhotosIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> database = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    SessionUser account() {
        UUID id = UUID.randomUUID(); String name = "photo_" + id.toString().substring(0, 8);
        jdbc.update("INSERT INTO app_users(id,username,email,password_hash) VALUES (?,?,?,?)", id, name, name+"@example.com", "test-only");
        return new SessionUser(id, name, name+"@example.com", "test-only", true);
    }
    UUID observation(SessionUser actor) {
        UUID map = UUID.randomUUID(), observation = UUID.randomUUID();
        jdbc.update("INSERT INTO mushroom_maps(id,owner_id,name) VALUES (?,?,'Kontrolna')", map, actor.id());
        jdbc.update("INSERT INTO observations(id,map_id,species_id,quantity,observed_on,location) VALUES (?,?,1,1,CURRENT_DATE,ST_SetSRID(ST_MakePoint(23,52),4326))", observation, map);
        return observation;
    }
    String upload(SessionUser actor, UUID observation) throws Exception {
        var result = mvc.perform(multipart("/api/observations/"+observation+"/photos")
                .file(PhotoProcessorTest.file("image/png", PhotoProcessorTest.image("png", 800, 400)))
                .with(user(actor)).with(csrf())).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get(0).get("id").asText();
    }

    @Test void privatePhotosHaveThumbnailsAndCascadeOnObservationAndMapDeletion() throws Exception {
        var actor = account(); UUID observation = observation(actor);
        String photo = upload(actor, observation);
        mvc.perform(get("/api/observations/"+observation+"/photos").with(user(actor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].width").value(800))
                .andExpect(jsonPath("$[0].storedFilename").doesNotExist()).andExpect(jsonPath("$[0].image").doesNotExist());
        mvc.perform(get("/api/photos/"+photo).with(user(actor)))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Cache-Control", "no-store"));
        byte[] thumbnail = mvc.perform(get("/api/photos/"+photo).param("thumbnail","true").with(user(actor)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(thumbnail)).getWidth()).isEqualTo(360);
        mvc.perform(delete("/api/photos/"+photo).with(user(actor)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/photos/"+photo).with(user(actor))).andExpect(status().isNotFound());
        upload(actor, observation);
        mvc.perform(delete("/api/observations/"+observation).with(user(actor)).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM observation_photos WHERE observation_id=?", Integer.class, observation)).isZero();
        UUID another = observation(actor); String anotherPhoto = upload(actor, another);
        UUID map = jdbc.queryForObject("SELECT map_id FROM observations WHERE id=?", UUID.class, another);
        mvc.perform(delete("/api/maps/"+map).with(user(actor)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/photos/"+anotherPhoto).with(user(actor))).andExpect(status().isNotFound());
    }

    @Test void foreignPhotosAndUnprotectedRequestsAreRejected() throws Exception {
        var owner = account(); var stranger = account(); UUID observation = observation(owner);
        String photo = upload(owner, observation);
        for (String path : new String[]{"/api/photos/"+photo,"/api/photos/"+photo+"?thumbnail=true","/api/observations/"+observation+"/photos"}) {
            mvc.perform(get(path).with(user(stranger))).andExpect(status().isNotFound());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        var image = PhotoProcessorTest.file("image/png", PhotoProcessorTest.image("png", 2, 2));
        mvc.perform(multipart("/api/observations/"+observation+"/photos").file(image).with(user(stranger)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/observations/"+observation+"/photos").file(image).with(user(owner))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/photos/"+photo).with(user(stranger)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/photos/"+photo).with(user(owner))).andExpect(status().isForbidden());
    }

    @Test void enforcesCountSizeAndTypeWithoutPartialUpload() throws Exception {
        var actor = account(); UUID observation = observation(actor);
        var image = PhotoProcessorTest.file("image/png", PhotoProcessorTest.image("png", 2, 2));
        String path = "/api/observations/"+observation+"/photos";
        mvc.perform(multipart(path).file(image).file(PhotoProcessorTest.file("image/svg+xml", "<svg/>".getBytes()))
                .with(user(actor)).with(csrf())).andExpect(status().isUnsupportedMediaType());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM observation_photos WHERE observation_id=?", Integer.class, observation)).isZero();
        mvc.perform(multipart(path).file(PhotoProcessorTest.file("image/png", new byte[5*1024*1024+1]))
                .with(user(actor)).with(csrf())).andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart(path).file(image).file(image).file(image).file(image).file(image)
                .with(user(actor)).with(csrf())).andExpect(status().isCreated());
        mvc.perform(multipart(path).file(image).with(user(actor)).with(csrf())).andExpect(status().isConflict());
    }
}
