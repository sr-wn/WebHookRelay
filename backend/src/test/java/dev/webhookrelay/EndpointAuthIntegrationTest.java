package dev.webhookrelay;

import dev.webhookrelay.service.JwtService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

/**
 * Verifies JWT auth + endpoint ownership enforcement end-to-end against real MySQL + Redis:
 * public capture works without a token, management requires one, and a token can only
 * read endpoints it created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EndpointAuthIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("webhookrelay");

    @Container
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7.4-alpine"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @LocalServerPort int port;

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpHeaders bearer(String ownerId) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwtService.issueToken(ownerId).getTokenValue());
        return h;
    }

    @Test
    void publicCaptureWorksWithoutToken() {
        // Create an endpoint as ownerA via token, then capture anonymously.
        String slug = createSlug(bearer("ownerA"));
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> capture = rest.exchange(
                base() + "/relay/" + slug, HttpMethod.POST,
                new HttpEntity<>("{\"x\":1}", json), String.class);
        assertThat(capture.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void managementRequiresToken() {
        ResponseEntity<String> noToken = rest.exchange(
                base() + "/api/endpoints", HttpMethod.POST, new HttpEntity<>(null), String.class);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenEndpointIssuesToken() {
        ResponseEntity<String> res = rest.exchange(
                base() + "/api/auth/token", HttpMethod.POST,
                new HttpEntity<>("{}", new HttpHeaders()), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("token").contains("ownerId");
    }

    @Test
    void ownerCanReadButOthersGetForbidden() {
        String slug = createSlug(bearer("ownerA"));

        // ownerA can read it
        ResponseEntity<String> ownerRead = rest.exchange(
                base() + "/api/endpoints/" + slug, HttpMethod.GET,
                new HttpEntity<>(null, bearer("ownerA")), String.class);
        assertThat(ownerRead.getStatusCode()).isEqualTo(HttpStatus.OK);

        // ownerB (different token) gets 403, not 404 — existence is not leaked as 404
        ResponseEntity<String> otherRead = rest.exchange(
                base() + "/api/endpoints/" + slug, HttpMethod.GET,
                new HttpEntity<>(null, bearer("ownerB")), String.class);
        assertThat(otherRead.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // requests list is likewise owner-scoped
        ResponseEntity<String> otherRequests = rest.exchange(
                base() + "/api/endpoints/" + slug + "/requests", HttpMethod.GET,
                new HttpEntity<>(null, bearer("ownerB")), String.class);
        assertThat(otherRequests.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> ownerRequests = rest.exchange(
                base() + "/api/endpoints/" + slug + "/requests", HttpMethod.GET,
                new HttpEntity<>(null, bearer("ownerA")), String.class);
        assertThat(ownerRequests.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void ownerCanReplayOwnRequest() throws Exception {
        String slug = createSlug(bearer("ownerA"));
        capture(slug, "{\"x\":1}");

        String requestId = firstRequestId(slug, "ownerA");
        com.sun.net.httpserver.HttpServer target = newTargetServer();
        int targetPort = target.getAddress().getPort();
        try {
            ResponseEntity<String> replay = rest.exchange(
                    base() + "/api/replay", HttpMethod.POST,
                    new HttpEntity<>(Map.of("requestId", requestId,
                            "targetUrl", "http://localhost:" + targetPort + "/"),
                            bearer("ownerA")), String.class);
            assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(replay.getBody()).contains("\"status\":200");
        } finally {
            target.stop(0);
        }
    }

    @Test
    void otherOwnerCannotReplayOrDiff() {
        String slug = createSlug(bearer("ownerA"));
        capture(slug, "{\"x\":1}");
        String requestId = firstRequestId(slug, "ownerA");

        ResponseEntity<String> replay = rest.exchange(
                base() + "/api/replay", HttpMethod.POST,
                new HttpEntity<>(Map.of("requestId", requestId,
                        "targetUrl", "http://localhost:1/"), bearer("ownerB")), String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> diff = rest.exchange(
                base() + "/api/replay/diff", HttpMethod.POST,
                new HttpEntity<>(Map.of("idA", requestId, "idB", requestId),
                        bearer("ownerB")), String.class);
        assertThat(diff.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void replayUnknownRequestReturns404() {
        ResponseEntity<String> replay = rest.exchange(
                base() + "/api/replay", HttpMethod.POST,
                new HttpEntity<>(Map.of("requestId", "00000000-0000-0000-0000-000000000000",
                        "targetUrl", "http://localhost:1/"), bearer("ownerA")), String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void capture(String slug, String body) {
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        rest.exchange(base() + "/relay/" + slug, HttpMethod.POST,
                new HttpEntity<>(body, json), String.class);
    }

    private String firstRequestId(String slug, String ownerId) {
        ResponseEntity<String> reqs = rest.exchange(
                base() + "/api/endpoints/" + slug + "/requests", HttpMethod.GET,
                new HttpEntity<>(null, bearer(ownerId)), String.class);
        assertThat(reqs.getStatusCode()).isEqualTo(HttpStatus.OK);
        return reqs.getBody().replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private com.sun.net.httpserver.HttpServer newTargetServer() throws Exception {
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        server.createContext("/", h -> h.sendResponseHeaders(200, -1));
        server.start();
        return server;
    }

    @Test
    void unknownSlugReturns404() {
        ResponseEntity<String> res = rest.exchange(
                base() + "/api/endpoints/does-not-exist", HttpMethod.GET,
                new HttpEntity<>(null, bearer("ownerA")), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String createSlug(HttpHeaders auth) {
        ResponseEntity<String> created = rest.exchange(
                base() + "/api/endpoints", HttpMethod.POST,
                new HttpEntity<>(null, auth), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Extract slug from JSON body minimally
        return created.getBody().replaceAll(".*\"slug\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
