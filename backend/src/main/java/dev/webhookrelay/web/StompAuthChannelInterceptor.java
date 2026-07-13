package dev.webhookrelay.web;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Map;

/**
 * Authenticates a STOMP CONNECT using the token captured at handshake time. A valid
 * token sets the session's principal to the token's owner ({@code sub}); the denylist
 * is enforced because the shared {@link JwtDecoder} already includes the revocation
 * validator. An invalid token rejects the connection; a missing token is allowed
 * (anonymous, slug-scoped — see WsTokenHandshakeInterceptor).
 */
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        String token = sessionAttrs == null ? null : (String) sessionAttrs.get(WsTokenHandshakeInterceptor.TOKEN_ATTR);
        if (token == null || token.isBlank()) {
            return message; // anonymous, allowed
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            String owner = jwt.getSubject();
            var auth = new UsernamePasswordAuthenticationToken(owner, null, AuthorityUtils.NO_AUTHORITIES);
            // Build a fresh mutable accessor: the one from getAccessor() is immutable.
            StompHeaderAccessor mutated = StompHeaderAccessor.wrap(message);
            mutated.setUser(auth);
            return MessageBuilder.createMessage(message.getPayload(), mutated.getMessageHeaders());
        } catch (JwtException | IllegalArgumentException e) {
            throw new MessageDeliveryException("WebSocket connection rejected: invalid or revoked token");
        }
    }
}
