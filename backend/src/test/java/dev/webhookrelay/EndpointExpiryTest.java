package dev.webhookrelay;

import dev.webhookrelay.domain.Endpoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointExpiryTest {

    @Test
    void notExpiredBeforeExpiry() {
        Endpoint e = new Endpoint();
        e.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(e.isExpired(Instant.now())).isFalse();
    }

    @Test
    void expiredAfterExpiry() {
        Endpoint e = new Endpoint();
        e.setExpiresAt(Instant.now().minus(1, ChronoUnit.SECONDS));
        assertThat(e.isExpired(Instant.now())).isTrue();
    }
}
