package dev.webhookrelay.config;

import dev.webhookrelay.web.StompAuthChannelInterceptor;
import dev.webhookrelay.web.WsTokenHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket configuration.
 *
 * IMPORTANT (see docs/architecture.md): this uses the *simple in-memory broker*
 * only to fan out to sessions connected to THIS instance. Cross-instance fan-out
 * is handled by {@link dev.webhookrelay.relay.RedisEventRelay}, which bridges a
 * Redis pub/sub channel to this local broker. That is what makes the app
 * horizontally scalable — a client on instance A receives events published by
 * instance B.
 *
 * <p>WebSocket auth is OPTIONAL: a client may connect anonymously and is then
 * scoped by the unguessable slug (the public capability model). If it presents a
 * bearer token (via {@code ?token=} on the SockJS URL or an Authorization header),
 * {@link StompAuthChannelInterceptor} authenticates it — reusing the same
 * denylist-aware {@link JwtDecoder} as the HTTP API, so revocation is enforced here too.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${webhookrelay.cors.allowed-origins}")
    private String[] allowedOrigins;

    private final JwtDecoder jwtDecoder;

    public WebSocketConfig(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .addInterceptors(new WsTokenHandshakeInterceptor())
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompAuthChannelInterceptor(jwtDecoder));
    }
}
