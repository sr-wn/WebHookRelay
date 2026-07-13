package dev.webhookrelay;

import dev.webhookrelay.config.RedisConfig;
import dev.webhookrelay.domain.Endpoint;
import dev.webhookrelay.repository.EndpointRepository;
import dev.webhookrelay.repository.RelayRequestRepository;
import dev.webhookrelay.service.EndpointService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full receive -> store -> broadcast flow against real MySQL + Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CaptureFlowIntegrationTest {

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
    @Autowired EndpointService endpointService;
    @Autowired RelayRequestRepository relayRequestRepository;
    @Autowired EndpointRepository endpointRepository;
    @Autowired RedisConnectionFactory redisConnectionFactory;
    @LocalServerPort int port;

    @Test
    void capturesStoresAndBroadcasts() throws Exception {
        Endpoint endpoint = endpointService.create(null);
        String slug = endpoint.getSlug();

        // Subscribe to the Redis events channel to prove broadcast happens.
        CountDownLatch broadcast = new CountDownLatch(1);
        RedisMessageListenerContainer listener = new RedisMessageListenerContainer();
        listener.setConnectionFactory(redisConnectionFactory);
        listener.addMessageListener(
                (message, pattern) -> broadcast.countDown(),
                new ChannelTopic(RedisConfig.EVENTS_CHANNEL));
        listener.afterPropertiesSet();
        listener.start();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> req = new HttpEntity<>("{\"hello\":\"world\"}", headers);

        var response = rest.exchange(
                "http://localhost:" + port + "/relay/" + slug,
                HttpMethod.POST, req, String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(broadcast.await(5, TimeUnit.SECONDS)).isTrue();

        var stored = relayRequestRepository
                .findByEndpointSlugOrderByReceivedAtDesc(slug,
                        org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(stored.getContent()).hasSize(1);
        assertThat(stored.getContent().get(0).getBody()).contains("world");

        listener.stop();
    }
}
