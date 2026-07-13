package dev.webhookrelay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import dev.webhookrelay.config.RedisConfig;
import dev.webhookrelay.relay.RedisEventRelay;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Proves the Redis relay fans out across instances.
 *
 * Two independent "instances" (each = its own listener container + relay + local
 * SimpMessagingTemplate) share one Redis. Publishing from instance A must result in
 * BOTH instances delivering to their local STOMP broker. This is the horizontal-
 * scalability guarantee that the in-memory broker alone cannot provide.
 */
@Testcontainers
class RedisFanoutTwoInstanceTest {

    @Container
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7.4-alpine"));

    static RedisConnectionFactory cf;
    static final ObjectMapper mapper = new ObjectMapper();
    static final ChannelTopic topic = new ChannelTopic(RedisConfig.EVENTS_CHANNEL);

    static SimpMessagingTemplate brokerA;
    static SimpMessagingTemplate brokerB;
    static RedisEventRelay relayA;
    static RedisMessageListenerContainer containerA;
    static RedisMessageListenerContainer containerB;

    @BeforeAll
    static void setUp() {
        LettuceConnectionFactory lettuce =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
        lettuce.afterPropertiesSet();
        cf = lettuce;

        StringRedisTemplate redis = new StringRedisTemplate(cf);

        brokerA = mock(SimpMessagingTemplate.class);
        brokerB = mock(SimpMessagingTemplate.class);

        relayA = new RedisEventRelay(redis, brokerA, mapper);
        RedisEventRelay relayB = new RedisEventRelay(redis, brokerB, mapper);

        containerA = listenerFor(relayA);
        containerB = listenerFor(relayB);
    }

    private static RedisMessageListenerContainer listenerFor(RedisEventRelay relay) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(relay, "onMessage");
        adapter.afterPropertiesSet();
        RedisMessageListenerContainer c = new RedisMessageListenerContainer();
        c.setConnectionFactory(cf);
        c.addMessageListener(adapter, topic);
        c.afterPropertiesSet();
        c.start();
        return c;
    }

    @AfterAll
    static void tearDown() {
        containerA.stop();
        containerB.stop();
    }

    @Test
    void eventPublishedByOneInstanceReachesBothInstances() throws Exception {
        String payload = mapper.writeValueAsString(
                java.util.Map.of("endpointSlug", "abc123", "method", "POST"));
        String dest = "/topic/endpoints/abc123";

        // Redis pub/sub does not queue for late subscribers, and the listener
        // containers subscribe asynchronously. Publish repeatedly until both
        // instances' local brokers have received at least once — proving fan-out.
        await().atMost(15, TimeUnit.SECONDS).pollInterval(250, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    relayA.publish("abc123", payload);
                    verify(brokerA, atLeastOnce()).convertAndSend(eq(dest), eq(payload));
                    verify(brokerB, atLeastOnce()).convertAndSend(eq(dest), eq(payload));
                });
    }
}
