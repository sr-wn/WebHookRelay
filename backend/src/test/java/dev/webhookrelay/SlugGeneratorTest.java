package dev.webhookrelay;

import dev.webhookrelay.service.SlugGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    private final SlugGenerator generator = new SlugGenerator();

    @Test
    void generatesRequestedLength() {
        assertThat(generator.generate(16)).hasSize(16);
        assertThat(generator.generate(24)).hasSize(24);
    }

    @Test
    void isUrlSafe() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.generate()).matches("[A-Za-z0-9_-]+");
        }
    }

    @Test
    void isEffectivelyUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            assertThat(seen.add(generator.generate())).isTrue();
        }
    }
}
