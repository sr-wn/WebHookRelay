package dev.webhookrelay.config;

import dev.webhookrelay.service.TokenDenylist;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Plugs into Spring Security's resource-server JWT validation: after the signature and
 * standard claims are verified, we reject the token if its {@code jti} is in the shared
 * Redis denylist. This is what makes stateless JWTs revocable across every instance.
 */
@Component
public class DenylistJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error("revoked", "Token has been revoked", null);

    private final TokenDenylist denylist;

    public DenylistJwtValidator(TokenDenylist denylist) {
        this.denylist = denylist;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (denylist.isRevoked(token.getId())) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
