package pl.mycomap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.mycomap.admin.AdminDtos;
import pl.mycomap.admin.AdminService;
import pl.mycomap.common.ApiException;
import pl.mycomap.security.SessionUser;
import pl.mycomap.user.UserRole;
import java.util.UUID;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class AdminIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> database = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired AdminService service;
    static final String PASSWORD = "Kontrola-Admina-2026!";

    @BeforeEach void cleanAccounts() { jdbc.update("DELETE FROM app_users"); }
    SessionUser account(UserRole role) {
        UUID id = UUID.randomUUID(); String name = "role_"+id.toString().substring(0,8);
        String hash = passwords.encode(PASSWORD);
        jdbc.update("INSERT INTO app_users(id,username,email,password_hash,email_verified,role) VALUES (?,?,?,?,true,?)",id,name,name+"@example.com",hash,role.name());
        return new SessionUser(id,name,name+"@example.com",hash,true,role,true,0);
    }
    MockHttpSession login(SessionUser actor) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("username",actor.username(),"password",PASSWORD))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    String update(UserRole role, boolean enabled) throws Exception { return json.writeValueAsString(new AdminDtos.UserInput(role,enabled)); }

    @Test void adminEndpointsRejectRegularUsersAndAdminsCannotReadPrivateMaps() throws Exception {
        var regular = account(UserRole.USER); var admin = account(UserRole.ADMIN);
        for (String path : new String[]{"/api/admin/users","/api/admin/species"}) {
            mvc.perform(get(path).with(user(regular))).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(patch("/api/admin/users/"+regular.id()).with(user(regular)).with(csrf()).contentType("application/json").content(update(UserRole.ADMIN,true))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/species").with(user(regular)).with(csrf()).contentType("application/json").content("{\"polishName\":\"Test\",\"isActive\":true}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(user(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        mvc.perform(patch("/api/admin/users/"+regular.id()).with(user(admin)).contentType("application/json").content(update(UserRole.ADMIN,true))).andExpect(status().isForbidden());
        UUID map = UUID.randomUUID(), observation = UUID.randomUUID(), photo = UUID.randomUUID();
        jdbc.update("INSERT INTO mushroom_maps(id,owner_id,name) VALUES (?,?,'Prywatna')",map,regular.id());
        jdbc.update("INSERT INTO observations(id,map_id,species_id,quantity,observed_on,location) VALUES (?,?,1,1,CURRENT_DATE,ST_SetSRID(ST_MakePoint(23,52),4326))",observation,map);
        jdbc.update("INSERT INTO observation_photos(id,observation_id,stored_filename,content_type,size_bytes,width,height,image_data,thumbnail_data) VALUES (?,?,?,'image/jpeg',1,1,1,?,?)",photo,observation,photo+".jpg",new byte[]{1},new byte[]{1});
        for (String path : new String[]{"/api/maps/"+map,"/api/observations/"+observation,"/api/photos/"+photo})
            mvc.perform(get(path).with(user(admin))).andExpect(status().isNotFound());
    }

    @Test void roleChangesAndBlockingInvalidateAllExistingSessions() throws Exception {
        var admin = account(UserRole.ADMIN); var regular = account(UserRole.USER);
        var first = login(regular); var second = login(regular);
        mvc.perform(patch("/api/admin/users/"+regular.id()).with(user(admin)).with(csrf()).contentType("application/json").content(update(UserRole.ADMIN,true))).andExpect(status().isOk());
        for (var session : new MockHttpSession[]{first,second})
            mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        var promoted = login(regular);
        mvc.perform(get("/api/admin/users").session(promoted)).andExpect(status().isOk());
        mvc.perform(patch("/api/admin/users/"+regular.id()).with(user(admin)).with(csrf()).contentType("application/json").content(update(UserRole.ADMIN,false))).andExpect(status().isOk());
        mvc.perform(get("/api/maps").session(promoted)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("username",regular.username(),"password",PASSWORD)))).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/users/"+regular.id()).with(user(admin)).with(csrf()).contentType("application/json").content(update(UserRole.USER,true))).andExpect(status().isOk());
        var restored = login(regular);
        mvc.perform(get("/api/admin/users").session(restored)).andExpect(status().isForbidden());
    }

    @Test void lastVerifiedEnabledAdminCannotBeBlockedOrDemoted() throws Exception {
        var admin = account(UserRole.ADMIN); var inactive = account(UserRole.ADMIN);
        jdbc.update("UPDATE app_users SET email_verified=false WHERE id=?",inactive.id());
        for (String input : new String[]{update(UserRole.USER,true),update(UserRole.ADMIN,false)}) {
            mvc.perform(patch("/api/admin/users/"+admin.id()).with(user(admin)).with(csrf()).contentType("application/json").content(input))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        }
        assertThat(jdbc.queryForObject("SELECT role FROM app_users WHERE id=?",String.class,admin.id())).isEqualTo("ADMIN");
    }

    @Test void concurrentSelfDemotionsLeaveOneActiveAdmin() throws Exception {
        var first = account(UserRole.ADMIN); var second = account(UserRole.ADMIN);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(java.util.List.of(
                () -> demote(first), () -> demote(second)));
            int successes = 0;
            for (var result : results) if (result.get().equals(200)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users WHERE role='ADMIN' AND account_enabled AND email_verified",Integer.class)).isEqualTo(1);
    }
    int demote(SessionUser actor) {
        try { service.updateUser(actor.id(),actor.id(),new AdminDtos.UserInput(UserRole.USER,true)); return 200; }
        catch (ApiException error) { return error.status().value(); }
    }

    @Test void speciesCanBeManagedWithoutBreakingExistingObservations() throws Exception {
        var admin = account(UserRole.ADMIN); var regular = account(UserRole.USER);
        var created = mvc.perform(post("/api/admin/species").with(user(admin)).with(csrf()).contentType("application/json")
                .content("{\"polishName\":\"Gatunek kontrolny\",\"latinName\":\"Testus\",\"isActive\":true}"))
                .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        assertThat(id).isGreaterThan(14);
        mvc.perform(post("/api/admin/species").with(user(admin)).with(csrf()).contentType("application/json")
                .content("{\"polishName\":\"GATUNEK KONTROLNY\",\"isActive\":true}")).andExpect(status().isConflict());
        UUID map = UUID.randomUUID(), observation = UUID.randomUUID();
        jdbc.update("INSERT INTO mushroom_maps(id,owner_id,name) VALUES (?,?,'Moja')",map,regular.id());
        jdbc.update("INSERT INTO observations(id,map_id,species_id,quantity,observed_on,location) VALUES (?,?,?,1,CURRENT_DATE,ST_SetSRID(ST_MakePoint(23,52),4326))",observation,map,id);
        mvc.perform(patch("/api/admin/species/"+id).with(user(admin)).with(csrf()).contentType("application/json")
                .content("{\"polishName\":\"Gatunek kontrolny\",\"isActive\":false}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mushroom_species WHERE id=? AND is_active",Integer.class,id)).isZero();
        String input = "{\"speciesId\":"+id+",\"quantity\":2,\"observedOn\":\"2026-10-01\",\"latitude\":52,\"longitude\":23}";
        mvc.perform(patch("/api/observations/"+observation).with(user(regular)).with(csrf()).contentType("application/json").content(input)).andExpect(status().isOk());
        mvc.perform(post("/api/maps/"+map+"/observations").with(user(regular)).with(csrf()).contentType("application/json").content(input)).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/species/1").with(user(admin)).with(csrf()).contentType("application/json")
                .content("{\"polishName\":\"Nieznany\",\"isActive\":false}")).andExpect(status().isConflict());
    }
}
