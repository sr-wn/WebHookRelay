package dev.webhookrelay.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.webhookrelay.config.RedisConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * The heart of horizontal WebSocket scalability.
 *
 * <p>Publish side: {@link #publish(String, String)} pushes a captured-event payload onto
 * a Redis pub/sub channel. Every app instance is subscribed to that channel.
 *
 * <p>Subscribe side: {@link #onMessage(String)} is invoked on EVERY instance when a
 * payload lands on the channel. Each instance then forwards it to its own locally
 * connected STOMP clients via {@link SimpMessagingTemplate}.
 *
 * <p>Net effect: a client subscribed on instance A receives events captured by
 * instance B. The in-memory simple broker only ever fans out to local sessions;
 * Redis provides the cross-instance bus. See docs/architecture.md.
 */
@Component
@RequiredArgsConstructor
public class RedisEventRelay {

    private static final Logger log = LoggerFactory.getLogger(RedisEventRelay.class);

    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /** Called by the capture flow. Payload is a JSON string of the RelayRequest view. */
    public void publish(String slug, String payloadJson) {
        redis.convertAndSend(RedisConfig.EVENTS_CHANNEL, payloadJson);
        log.debug("Published event for slug={} to redis channel", slug);
    }

    /** Invoked by RedisMessageListenerContainer on every instance. */
    public void onMessage(String payloadJson) {
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            String slug = node.path("endpointSlug").asText();
            if (slug.isEmpty()) {
                log.warn("Received relay event without endpointSlug, dropping");
                return;
            }
            messagingTemplate.convertAndSend("/topic/endpoints/" + slug, payloadJson);
            log.debug("Fanned out event for slug={} to local STOMP sessions", slug);
        } catch (Exception e) {
            log.error("Failed to relay redis event to STOMP", e);
        }
    }
}
