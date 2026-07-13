package dev.webhookrelay;

import dev.webhookrelay.config.WebhookRelayProperties;
import dev.webhookrelay.service.JwtService;
import dev.webhookrelay.service.TokenDenylist;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.JWSAlgorithm;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "dev-only-insecure-secret-change-me-0123456789";

    private JwtService service() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        // alg must be set explicitly or newer Nimbus can't select the signing key.
        JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource =
                new ImmutableJWKSet<>(new JWKSet(
                        new OctetSequenceKey.Builder(key).algorithm(JWSAlgorithm.HS256).build()));
        WebhookRelayProperties props = new WebhookRelayProperties(
                Duration.ofHours(24), 1, Duration.ofSeconds(30),
                new WebhookRelayProperties.RateLimit(1, 1, 1, 1),
                new WebhookRelayProperties.Jwt(SECRET, Duration.ofHours(12)));
        return new JwtService(new NimbusJwtEncoder(jwkSource),
                NimbusJwtDecoder.withSecretKey(key).build(), props,
                org.mockito.Mockito.mock(TokenDenylist.class));
    }

    @Test
    void roundTripsOwnerIdAndExpiry() {
        JwtService svc = service();
        Jwt token = svc.issueToken("owner-abc");

        assertThat(token.getSubject()).isEqualTo("owner-abc");
        assertThat(token.getExpiresAt()).isAfter(Instant.now());

        Jwt parsed = svc.parse(token.getTokenValue());
        assertThat(parsed.getSubject()).isEqualTo("owner-abc");
    }

    @Test
    void generatesOwnerIdWhenNull() {
        Jwt token = service().issueToken(null);
        assertThat(token.getSubject()).isNotBlank();
    }
}
