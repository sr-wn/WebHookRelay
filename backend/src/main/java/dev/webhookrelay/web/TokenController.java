package dev.webhookrelay.web;

import dev.webhookrelay.service.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Mints stateless JWTs that establish endpoint ownership. Account-less by design:
 * a client with no session asks for a token (optionally re-using a previously known
 * ownerId, so a browser can keep owning its endpoints across reloads). The returned
 * token's {@code sub} claim is the ownerId, which every management call is scoped to.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TokenController {

    private final JwtService jwtService;

    public record TokenRequest(
            @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "ownerId must be a UUID v4")
            String ownerId) {}

    public record TokenResponse(String token, String ownerId, java.time.Instant expiresAt) {}

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> token(@Valid @RequestBody(required = false) TokenRequest body) {
        String ownerId = (body != null && body.ownerId() != null) ? body.ownerId() : UUID.randomUUID().toString();
        var issued = jwtService.issueToken(ownerId);
        return ResponseEntity.ok(new TokenResponse(issued.getTokenValue(), ownerId, issued.getExpiresAt()));
    }

    /**
     * Revoke the caller's own bearer token (by its {@code jti}) until it would naturally
     * expire. Enforced globally via the Redis denylist — useful on logout / suspected leak.
     */
    @PostMapping("/revoke")
    public ResponseEntity<?> revoke(@org.springframework.web.bind.annotation.RequestHeader(
            value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "missing_bearer_token"));
        }
        try {
            jwtService.revoke(authorization.substring(7).trim());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "invalid_token"));
        }
        return ResponseEntity.ok(java.util.Map.of("status", "revoked"));
    }
}
