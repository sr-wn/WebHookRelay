package dev.webhookrelay.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock JwtDecoder jwtDecoder;
    private StompAuthChannelInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setup() {
        interceptor = new StompAuthChannelInterceptor(jwtDecoder);
    }

    private Message<?> connectWithToken(String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Map<String, Object> attrs = new HashMap<>();
        if (token != null) {
            attrs.put(WsTokenHandshakeInterceptor.TOKEN_ATTR, token);
        }
        accessor.setSessionAttributes(attrs);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private String userOf(Message<?> message) {
        return StompHeaderAccessor.wrap(message).getUser() == null
                ? null : StompHeaderAccessor.wrap(message).getUser().getName();
    }

    @Test
    void authenticatesValidTokenAndSetsOwnerPrincipal() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("owner-1").build();
        when(jwtDecoder.decode("good")).thenReturn(jwt);

        Message<?> result = interceptor.preSend(connectWithToken("good"), channel);

        assertThat(userOf(result)).isEqualTo("owner-1");
    }

    @Test
    void rejectsInvalidToken() {
        when(jwtDecoder.decode("bad")).thenThrow(new BadJwtException("nope"));

        assertThatThrownBy(() -> interceptor.preSend(connectWithToken("bad"), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void allowsAnonymousConnectionWithoutToken() {
        Message<?> result = interceptor.preSend(connectWithToken(null), channel);
        assertThat(userOf(result)).isNull();
    }

    @Test
    void ignoresNonConnectFrames() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionAttributes(new HashMap<>());
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(msg, channel);
        assertThat(result).isSameAs(msg);
        // and it must not attempt JWT decoding
        assertThat(userOf(result)).isNull();
    }
}
