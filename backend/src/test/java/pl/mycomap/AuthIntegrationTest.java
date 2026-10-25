package pl.mycomap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mail;
    @Value("${local.server.port}") int port;
    static final String PASSWORD = "Lesna-wyprawa-2026";

    String username() { return "les_" + UUID.randomUUID().toString().substring(0, 12); }
    String body(String username, String email, String password) throws Exception {
        return json.writeValueAsString(Map.of("username", username, "email", email, "password", password));
    }
    void register(String name) throws Exception { register(name, true); }
    void register(String name, boolean activate) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                        .content(body(name, name + "@example.com", PASSWORD)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        if (activate) verifyToken(emailToken());
    }
    String emailToken() {
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(messages.capture());
        return messages.getValue().getText().split("#token=")[1].split("\\s")[0];
    }
    void verifyToken(String token) throws Exception {
        mvc.perform(post("/api/auth/verify-email").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("token", token)))).andExpect(status().isOk());
    }

    @Test void unverifiedAccountCannotLogInAndTokenIsSingleUse() throws Exception {
        String name = username(); register(name, false);
        String token = emailToken();
        assertThat(jdbc.queryForObject("SELECT verification_hash FROM app_users WHERE username = ?", String.class, name)).hasSize(64).isNotEqualTo(token);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(body(name, "unused@example.com", PASSWORD)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        verifyToken(token);
        mvc.perform(post("/api/auth/verify-email").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VERIFICATION_INVALID"));
    }

    @Test void expiredLinkCanBeReplacedWithCooldownAndNoAccountDisclosure() throws Exception {
        String name = username(); register(name, false);
        String oldToken = emailToken();
        jdbc.update("UPDATE app_users SET verification_expires_at = now() - interval '1 hour' WHERE username = ?", name);
        mvc.perform(post("/api/auth/verify-email").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("token", oldToken))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/resend-verification").with(csrf()).contentType("application/json").content("{\"email\":\"" + name + "@example.com\"}"))
                .andExpect(status().isOk());
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        jdbc.update("UPDATE app_users SET verification_sent_at = now() - interval '2 minutes' WHERE username = ?", name);
        var known = mvc.perform(post("/api/auth/resend-verification").with(csrf()).contentType("application/json").content("{\"email\":\"" + name + "@example.com\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var unknown = mvc.perform(post("/api/auth/resend-verification").with(csrf()).contentType("application/json").content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(known).isEqualTo(unknown);
        String newToken = emailToken();
        assertThat(newToken).isNotEqualTo(oldToken);
        verifyToken(newToken);
    }

    @Test void mailFailureRollsBackRegistration() throws Exception {
        String name = username();
        doThrow(new MailSendException("test SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body(name, name + "@example.com", PASSWORD)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MAIL_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users WHERE username = ?", Integer.class, name)).isZero();
    }

    @Test void registrationHashesPasswordAndDoesNotLogIn() throws Exception {
        String name = username();
        register(name);
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_users WHERE username = ?", String.class, name);
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(PASSWORD, hash)).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test void registrationRejectsCaseInsensitiveDuplicates() throws Exception {
        String name = username(); register(name);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content(body(name.toUpperCase(), "other_" + name + "@example.com", PASSWORD)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS"));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content(body(username(), (name + "@example.com").toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test void validationRejectsInvalidNamesEmailsAndBcryptOverflow() throws Exception {
        for (String input : new String[]{body("a", "bad", "short"), body(username(), "ok@example.com", "ą".repeat(40))}) {
            mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(input))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }

    @Test void loginUsesSessionAndLogoutInvalidatesIt() throws Exception {
        String name = username(); register(name);
        var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(body(name.toUpperCase(), "ignored@example.com", PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(name)).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(name + "@example.com"));
        mvc.perform(get("/api/maps").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void failedLoginIsGenericAndDoesNotAuthenticate() throws Exception {
        String name = username(); register(name);
        for (String candidate : new String[]{name, username()}) {
            mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                    .content(body(candidate, "ignored@example.com", "incorrect-password")))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Nieprawidłowa nazwa użytkownika lub hasło."));
        }
    }

    @Test void allPublicMutationsAndLogoutRequireCsrf() throws Exception {
        for (String path : new String[]{"register", "login", "logout", "verify-email", "resend-verification"}) {
            mvc.perform(post("/api/auth/" + path).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        }
    }

    @Test void realBrowserSessionRotatesIdAndCsrfAndExpiresOnLogout() throws Exception {
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String name = username();
        var csrfResponse = send(client, "GET", "/api/auth/csrf", null, null);
        String initialCookie = csrfResponse.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
        assertThat(csrfResponse.headers().firstValue("set-cookie").orElseThrow()).contains("HttpOnly", "SameSite=Lax");
        String token = json.readTree(csrfResponse.body()).get("token").asText();
        assertThat(send(client, "POST", "/api/auth/register", token, body(name, name + "@example.com", PASSWORD)).statusCode()).isEqualTo(201);
        verifyToken(emailToken());
        var login = send(client, "POST", "/api/auth/login", token, body(name, "unused@example.com", PASSWORD));
        assertThat(login.statusCode()).isEqualTo(200);
        String newCookie = login.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
        assertThat(newCookie).isNotEqualTo(initialCookie);
        assertThat(send(client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
        var replay = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base() + "/api/auth/me"))
                .header("Cookie", initialCookie).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(replay.statusCode()).isEqualTo(401);
        assertThat(send(client, "POST", "/api/auth/logout", token, "{}").statusCode()).isEqualTo(403);
        JsonNode fresh = json.readTree(send(client, "GET", "/api/auth/csrf", null, null).body());
        assertThat(send(client, "POST", "/api/auth/logout", fresh.get("token").asText(), "{}").statusCode()).isEqualTo(204);
        assertThat(send(client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
    }

    String base() { return "http://localhost:" + port; }
    HttpResponse<String> send(HttpClient client, String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + path)).header("Content-Type", "application/json");
        if (token != null) request.header("X-CSRF-TOKEN", token);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
