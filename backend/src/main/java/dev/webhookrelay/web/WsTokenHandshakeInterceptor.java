package dev.webhookrelay.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Captures an optional bearer token from the WebSocket upgrade request (either the
 * {@code token} query param on the SockJS URL, or an {@code Authorization: Bearer}
 * header) and stashes it in the session attributes, where the STOMP auth interceptor
 * can pick it up on CONNECT. The token is OPTIONAL: no token means an anonymous,
 * slug-scoped session (the public "anyone with the unguessable slug" capability model).
 */
public class WsTokenHandshakeInterceptor implements HandshakeInterceptor {

    static final String TOKEN_ATTR = "ws.jwt";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest servlet = servletRequest.getServletRequest();
            String token = servlet.getParameter("token");
            if (token == null || token.isBlank()) {
                String authz = servlet.getHeader("Authorization");
                if (authz != null && authz.startsWith("Bearer ")) {
                    token = authz.substring(7).trim();
                }
            }
            if (token != null && !token.isBlank()) {
                attributes.put(TOKEN_ATTR, token);
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
