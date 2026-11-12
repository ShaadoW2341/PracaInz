package pl.mycomap;

import com.fasterxml.jackson.databind.JsonNode;
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
import pl.mycomap.user.UserRole;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class SharingIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> database=new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    record Finding(UUID map,UUID observation,UUID photo) {}
    SessionUser account(UserRole role) {
        UUID id=UUID.randomUUID(); String name="share_"+id.toString().substring(0,8);
        jdbc.update("INSERT INTO app_users(id,username,email,password_hash,email_verified,role) VALUES (?,?,?,'test-only',true,?)",id,name,name+"@example.com",role.name());
        return new SessionUser(id,name,name+"@example.com","test-only",true,role,true,0);
    }
    SessionUser account() { return account(UserRole.USER); }
    Finding finding(SessionUser owner) {
        UUID map=UUID.randomUUID(),observation=UUID.randomUUID(),photo=UUID.randomUUID();
        jdbc.update("INSERT INTO mushroom_maps(id,owner_id,name,description) VALUES (?,?,'Las','Prywatny opis mapy')",map,owner.id());
        jdbc.update("INSERT INTO observations(id,map_id,species_id,quantity,observed_on,description,location) VALUES (?,?,2,3,'2026-10-01','Prywatna notatka',ST_SetSRID(ST_MakePoint(23.865123,52.704321),4326))",observation,map);
        jdbc.update("INSERT INTO observation_photos(id,observation_id,stored_filename,content_type,size_bytes,width,height,image_data,thumbnail_data) VALUES (?,?,?,'image/jpeg',1,1,1,?,?)",photo,observation,photo+".jpg",new byte[]{1},new byte[]{1});
        return new Finding(map,observation,photo);
    }
    JsonNode read(SessionUser actor,String path) throws Exception {
        return json.readTree(mvc.perform(get(path).with(user(actor))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    UUID share(SessionUser owner,Finding finding,SessionUser recipient) throws Exception {
        var result=mvc.perform(post("/api/maps/"+finding.map()+"/shares").with(user(owner)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username",recipient.username())))).andExpect(status().isCreated()).andReturn();
        var data=json.readTree(result.getResponse().getContentAsString());
        assertThat(data.get("precision").asText()).isEqualTo("COARSE");
        return UUID.fromString(data.get("id").asText());
    }
    String box(double lat,double lon) { return String.format(Locale.ROOT,"%.9f,%.9f,%.9f,%.9f",lon-0.0000001,lat-0.0000001,lon+0.0000001,lat+0.0000001); }

    @Test void coarseCoordinatesAndBboxNeverExposeOriginalLocation() throws Exception {
        var owner=account(); var recipient=account(); var finding=finding(owner); share(owner,finding,recipient);
        var original=read(owner,"/api/observations/"+finding.observation());
        var masked=read(recipient,"/api/observations/"+finding.observation());
        assertThat(original.get("latitude").asDouble()).isEqualTo(52.704321);
        assertThat(masked.get("latitude").asDouble()).isNotEqualTo(52.704321);
        assertThat(masked.get("longitude").asDouble()).isNotEqualTo(23.865123);
        assertThat(masked.toString()).doesNotContain("52.704321","23.865123","Prywatna notatka");
        assertThat(read(recipient,"/api/maps/"+finding.map()).get("canEdit").asBoolean()).isFalse();
        assertThat(read(recipient,"/api/maps/"+finding.map()).get("description").asText()).isEmpty();
        assertThat(read(owner,"/api/maps/"+finding.map()).get("canEdit").asBoolean()).isTrue();
        assertThat(read(recipient,"/api/maps/shared-with-me").get("total").asInt()).isEqualTo(1);
        assertThat(read(recipient,"/api/maps").get("items").toString()).doesNotContain(finding.map().toString());
        String originalBox=box(52.704321,23.865123),maskedBox=box(masked.get("latitude").asDouble(),masked.get("longitude").asDouble());
        mvc.perform(get("/api/maps/"+finding.map()+"/observations").param("bbox",originalBox).with(user(recipient)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/maps/"+finding.map()+"/observations/geojson").param("bbox",originalBox).with(user(recipient)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.features.length()").value(0));
        var geo=mvc.perform(get("/api/maps/"+finding.map()+"/observations/geojson").param("bbox",maskedBox).with(user(recipient)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.features.length()").value(1)).andReturn();
        var coordinates=json.readTree(geo.getResponse().getContentAsString()).get("features").get(0).get("geometry").get("coordinates");
        assertThat(coordinates.get(0).asDouble()).isEqualTo(masked.get("longitude").asDouble());
        assertThat(coordinates.get(1).asDouble()).isEqualTo(masked.get("latitude").asDouble());
        double distance=jdbc.queryForObject("SELECT ST_Distance(location::geography,mycomap_coarse_location(location)::geography) FROM observations WHERE id=?",Double.class,finding.observation());
        assertThat(distance).isBetween(0.0,1000.0);
        assertThat(jdbc.queryForObject("SELECT ST_X(location) FROM observations WHERE id=?",Double.class,finding.observation())).isEqualTo(23.865123);
    }

    @Test void exactRequiresConfirmationAndRevocationImmediatelyBlocksEveryRead() throws Exception {
        var owner=account(); var recipient=account(); var finding=finding(owner); UUID id=share(owner,finding,recipient);
        String path="/api/maps/"+finding.map()+"/shares/"+id;
        mvc.perform(patch(path).with(user(owner)).with(csrf()).contentType("application/json").content("{\"precision\":\"EXACT\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EXACT_CONFIRMATION_REQUIRED"));
        mvc.perform(patch(path).with(user(owner)).with(csrf()).contentType("application/json").content("{\"precision\":\"EXACT\",\"exactConfirmed\":true}")).andExpect(status().isOk());
        assertThat(read(recipient,"/api/observations/"+finding.observation()).get("latitude").asDouble()).isEqualTo(52.704321);
        assertThat(read(recipient,"/api/observations/"+finding.observation()).get("description").asText()).isEqualTo("Prywatna notatka");
        mvc.perform(patch(path).with(user(owner)).with(csrf()).contentType("application/json").content("{\"precision\":\"COARSE\"}")).andExpect(status().isOk());
        assertThat(read(recipient,"/api/observations/"+finding.observation()).get("latitude").asDouble()).isNotEqualTo(52.704321);
        mvc.perform(delete(path).with(user(owner)).with(csrf())).andExpect(status().isNoContent());
        for(String read:new String[]{"/api/maps/"+finding.map(),"/api/maps/"+finding.map()+"/observations","/api/maps/"+finding.map()+"/observations/geojson?bbox=23,52,24,53","/api/observations/"+finding.observation(),"/api/observations/"+finding.observation()+"/photos","/api/photos/"+finding.photo()})
            mvc.perform(get(read).with(user(recipient))).andExpect(status().isNotFound());
        assertThat(read(recipient,"/api/maps/shared-with-me").get("total").asInt()).isZero();
    }

    @Test void sharingIsReadOnlyAndPrivatePhotosRemainOwnerOnly() throws Exception {
        var owner=account(); var recipient=account(); var stranger=account(); var admin=account(UserRole.ADMIN); var finding=finding(owner); UUID share=share(owner,finding,recipient);
        mvc.perform(patch("/api/maps/"+finding.map()+"/shares/"+share).with(user(owner)).with(csrf()).contentType("application/json").content("{\"precision\":\"EXACT\",\"exactConfirmed\":true}")).andExpect(status().isOk());
        for(var actor:List.of(stranger,admin)) {
            mvc.perform(get("/api/maps/"+finding.map()).with(user(actor))).andExpect(status().isNotFound());
            mvc.perform(get("/api/observations/"+finding.observation()).with(user(actor))).andExpect(status().isNotFound());
        }
        String observation="{\"speciesId\":1,\"quantity\":1,\"observedOn\":\"2026-10-01\",\"latitude\":52,\"longitude\":23}";
        mvc.perform(patch("/api/maps/"+finding.map()).with(user(recipient)).with(csrf()).contentType("application/json").content("{\"name\":\"Zmieniona\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/maps/"+finding.map()).with(user(recipient)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/maps/"+finding.map()+"/observations").with(user(recipient)).with(csrf()).contentType("application/json").content(observation)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/observations/"+finding.observation()).with(user(recipient)).with(csrf()).contentType("application/json").content(observation)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/observations/"+finding.observation()).with(user(recipient)).with(csrf())).andExpect(status().isNotFound());
        for(String path:new String[]{"/api/observations/"+finding.observation()+"/photos","/api/photos/"+finding.photo(),"/api/photos/"+finding.photo()+"?thumbnail=true","/api/maps/"+finding.map()+"/shares"})
            mvc.perform(get(path).with(user(recipient))).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/observations/"+finding.observation()+"/photos").file(PhotoProcessorTest.file("image/png",PhotoProcessorTest.image("png",2,2))).with(user(recipient)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/photos/"+finding.photo()).with(user(recipient)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/maps/"+finding.map()+"/shares").with(user(recipient)).with(csrf()).contentType("application/json").content("{\"username\":\""+stranger.username()+"\"}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/maps/"+finding.map()+"/shares/"+share).with(user(recipient)).with(csrf()).contentType("application/json").content("{\"precision\":\"COARSE\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/maps/"+finding.map()+"/shares/"+share).with(user(recipient)).with(csrf())).andExpect(status().isNotFound());
    }

    @Test void validatesRecipientsScopesSharesAndRequiresCsrf() throws Exception {
        var owner=account(); var recipient=account(); var finding=finding(owner); var other=finding(owner);
        var matches=read(owner,"/api/users/search?query="+recipient.username());
        assertThat(matches.size()).isEqualTo(1); assertThat(matches.toString()).doesNotContain("email","role","password");
        assertThat(read(owner,"/api/users/search?query=sh").size()).isZero();
        assertThat(read(owner,"/api/users/search?query="+owner.username()).size()).isZero();
        String root="/api/maps/"+finding.map()+"/shares";
        mvc.perform(post(root).with(user(owner)).contentType("application/json").content("{\"username\":\""+recipient.username()+"\"}")).andExpect(status().isForbidden());
        mvc.perform(post(root).with(user(owner)).with(csrf()).contentType("application/json").content("{\"username\":\""+owner.username()+"\"}")).andExpect(status().isBadRequest());
        UUID id=share(owner,finding,recipient);
        mvc.perform(post(root).with(user(owner)).with(csrf()).contentType("application/json").content("{\"username\":\""+recipient.username()+"\"}")).andExpect(status().isConflict());
        mvc.perform(delete(root+"/"+id).with(user(owner))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/maps/"+other.map()+"/shares/"+id).with(user(owner)).with(csrf())).andExpect(status().isNotFound());
        jdbc.update("UPDATE app_users SET account_enabled=false WHERE id=?",recipient.id());
        mvc.perform(post("/api/maps/"+other.map()+"/shares").with(user(owner)).with(csrf()).contentType("application/json").content("{\"username\":\""+recipient.username()+"\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/maps/"+finding.map()).with(user(owner)).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM map_shares WHERE id=?",Integer.class,id)).isZero();
    }

    @Test void coarseGridSupportsDateLineEquatorAndPoles() {
        for(double[] point:new double[][]{{180,90},{-180,-90},{0,0},{23.865123,52.704321},{-170,-60}}) {
            var coordinates=jdbc.queryForMap("SELECT ST_X(p) lon,ST_Y(p) lat FROM (SELECT mycomap_coarse_location(ST_SetSRID(ST_MakePoint(?,?),4326)) p) x",point[0],point[1]);
            assertThat(((Number)coordinates.get("lon")).doubleValue()).isBetween(-180.0,180.0);
            assertThat(((Number)coordinates.get("lat")).doubleValue()).isBetween(-90.0,90.0);
        }
    }
}
