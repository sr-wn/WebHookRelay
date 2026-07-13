package dev.webhookrelay.config;

import dev.webhookrelay.service.TokenDenylist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DenylistJwtValidatorTest {

    @Mock TokenDenylist denylist;
    private DenylistJwtValidator validator;

    @BeforeEach
    void setup() {
        validator = new DenylistJwtValidator(denylist);
    }

    private Jwt jwtWithJti(String jti) {
        return Jwt.withTokenValue("token").header("alg", "HS256").claim("jti", jti).build();
    }

    @Test
    void rejectsRevokedJti() {
        when(denylist.isRevoked("jti-9")).thenReturn(true);
        assertThat(validator.validate(jwtWithJti("jti-9")).hasErrors()).isTrue();
    }

    @Test
    void acceptsNonRevokedJti() {
        when(denylist.isRevoked("jti-10")).thenReturn(false);
        assertThat(validator.validate(jwtWithJti("jti-10")).hasErrors()).isFalse();
    }
}
