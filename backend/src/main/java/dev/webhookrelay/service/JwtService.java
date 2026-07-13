package dev.webhookrelay.service;

import dev.webhookrelay.config.WebhookRelayProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues and decodes the bearer tokens that establish {@code ownerId} (endpoint
 * ownership). Stateless: a token's {@code sub} claim IS the owner, so no session
 * store is needed and any instance can verify it (important for horizontal scale).
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final WebhookRelayProperties.Jwt jwtProps;
    private final TokenDenylist denylist;

    public JwtService(JwtEncoder encoder, JwtDecoder decoder, WebhookRelayProperties props,
                      TokenDenylist denylist) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.jwtProps = props.jwt();
        this.denylist = denylist;
    }

    /** Mint a fresh token for the given owner. A new random ownerId if you pass null. */
    public Jwt issueToken(String ownerId) {
        String subject = (ownerId != null && !ownerId.isBlank()) ? ownerId : newOwnerId();
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString()) // jti — required for individual revocation
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(jwtProps.tokenTtl()))
                .build();
        // Must pin HS256 explicitly: JwtEncoderParameters.from(claims) defaults the
        // JWS alg to RS256, which would never match our HMAC (OCT) signing key.
        return encoder.encode(JwtEncoderParameters.from(header, claims));
    }

    /** Verify + parse a raw token (used on the wire by Spring Security). */
    public Jwt parse(String token) {
        return decoder.decode(token);
    }

    /**
     * Revoke a token by its {@code jti} until its natural expiry. Enforced across all
     * instances because the denylist lives in Redis. A leaked token can thus be
     * invalidated before its TTL elapses.
     */
    public void revoke(String tokenValue) {
        Jwt jwt = parse(tokenValue);
        denylist.revoke(jwt.getId(), jwt.getExpiresAt());
    }

    public boolean isRevoked(String jti) {
        return denylist.isRevoked(jti);
    }

    public Duration tokenTtl() {
        return jwtProps.tokenTtl();
    }

    public static String newOwnerId() {
        return UUID.randomUUID().toString();
    }
}
